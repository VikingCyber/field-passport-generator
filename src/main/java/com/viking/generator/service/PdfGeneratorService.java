package com.viking.generator.service;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.NoteImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.tables.NoteTableRow;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import com.viking.generator.util.FileUtils;
import com.viking.generator.util.ImageOptimizer;
import com.viking.generator.util.NoteImageComparators;
import com.viking.generator.util.PassportPageEventHandler;
import com.viking.generator.util.PdfUIHelper;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PdfGeneratorService implements PassportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    /** Названия разделов паспорта — единый источник для заголовков и содержания. */
    static final String SECTION_1 = "Раздел 1. Общая информация.";
    static final String SECTION_2 = "Раздел 2. Выполненные работы";
    static final String SECTION_3 = "Раздел 3. Заметки";
    static final String SECTION_4 = "Раздел 4. Индексы";
    static final String SECTION_5 = "Раздел 5. Спутниковые снимки NDVI";
    static final String SECTION_6 = "Раздел 6. Работающие механизаторы, техника, агрегаты.";

    /**
     * Сдвиг страниц разделов относительно чернового прохода: финальный документ
     * получает одну страницу содержания перед «Разделом 1». Если содержание
     * перестанет помещаться на одну страницу, сдвиг надо пересчитать.
     */
    private static final int TOC_PAGE_SHIFT = 1;

    private final long minRequiredSpaceBytes;
    private final Path outputDir;

    public PdfGeneratorService(long minRequiredSpaceBytes, Path outputDir) {
        this.minRequiredSpaceBytes = minRequiredSpaceBytes;
        this.outputDir = Objects.requireNonNull(outputDir, "OutputDir must not be null");
    }


    @Override
    public void generate(FieldPassport passport) {
        Path filePath = resolvePassportPath(passport);
        String uniqueTempName = filePath.getFileName().toString()
                .replace(".pdf", "_" + UUID.randomUUID() + ".tmp");
        Path tempPath = filePath.resolveSibling(uniqueTempName);
        log.info("==> Старт генерации PDF для поля: {} (Файл: {})",
                passport.generalInfo().fieldName(), filePath.getFileName());

        checkStorageSafety(outputDir);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Критическая ошибка: не удалось подготовить директорию {}. Причина: {}",
                    outputDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Не удалось создать директорию", e);
        }

        boolean isMovedSuccessfully = false;

        try {
            // Изображения сжимаются ОДИН раз до двух проходов рендера. Раньше оптимизация
            // выполнялась внутри каждого прохода (картинки декодировались и пережимались
            // дважды без видимого прогресса в логах) — это было основной причиной долгой
            // генерации.
            optimizeImagesOnce(passport);

            // Pass 1 — черновик в память: узнаём номера страниц разделов.
            // Отрисовка детерминирована: те же данные и тот же код дают те же разрывы страниц,
            // поэтому в финальном файле разделы смещены ровно на страницу содержания.
            SectionPageRecorder recorder = new SectionPageRecorder();
            log.info("Проход 1/2: черновой рендер разделов (сбор содержания)...");
            renderSectionsToBytes(passport, recorder);
            log.info("Проход 2/2: финальный рендер с содержанием...");

            renderToTemp(tempPath, passport, recorder);
        } catch (DocumentException e) {
            log.error("Ошибка структуры PDF (OpenPDF) для {}: {}",
                    passport.generalInfo().fieldName(), e.getMessage());
            throw new RuntimeException("Ошибка форматирования PDF", e);
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода для {}: {}", filePath.getFileName(), e.getMessage());
            throw new RuntimeException("Ошибка файловой системы", e);
        }
        try {
            FileUtils.atomicMoveWithRetries(tempPath, filePath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            isMovedSuccessfully = true;
            log.info("==> Документ успешно зафиксирован на диске: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Критическая ошибка атомарной подмены файла для {}", filePath.getFileName(),
                    e);
            throw new RuntimeException("Не удалось обновить финальный PDF файл", e);
        } finally {
            if (!isMovedSuccessfully) {
                log.warn("Генерация сорвалась или прервана. Подчищаем временный файл: {}",
                        tempPath.getFileName());
                FileUtils.deleteWithRetries(tempPath);
            }
        }
        log.info("==> Генерация завершена. Путь: {}", filePath.toAbsolutePath());
    }

    private void checkStorageSafety(Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            if (!Files.isWritable(outputDir)) {
                throw new IOException("Отсутствуют права на запись");
            }

            FileStore store = Files.getFileStore(outputDir);
            if (store.getUsableSpace() < minRequiredSpaceBytes) {
                throw new IOException("Недостаточно свободного места на диске");
            }
        } catch (IOException e) {
            log.error("Директория: {} не готова к работе: {}", outputDir.toAbsolutePath(),
                    e.getMessage());
            throw new RuntimeException("Подготовка хранилища не удалась", e);
        }
    }

    /**
     * Сжимает все изображения паспорта ОДИН раз перед двумя проходами рендера
     * и кладёт сжатые байты обратно в модель. При следующей генерации
     * {@code ImageCacheService.fillImages} заново прочитает оригиналы с диска,
     * поэтому модель не накапливает сжатие, а кэш на диске не трогается.
     * Прогресс логируется, чтобы было видно, что генерация работает, а не висит.
     */
    private void optimizeImagesOnce(FieldPassport passport) {
        List<NoteImage> notes = passport.notesSection() != null
                ? passport.notesSection().images() : null;
        List<SatelliteImage> sats = passport.satelliteImages();

        int total = 0;
        if (notes != null) {
            total += (int) notes.stream().filter(n -> n.getImageBytes() != null).count();
        }
        if (sats != null) {
            total += (int) sats.stream().filter(s -> s.getImageBytes() != null).count();
        }
        if (total == 0) {
            log.debug("Изображений для сжатия нет.");
            return;
        }

        log.info("Сжатие изображений: 0/{} (один общий прогон для черновика и финала)", total);
        int done = 0;
        if (notes != null) {
            for (NoteImage n : notes) {
                if (n.getImageBytes() == null) {
                    continue;
                }
                n.setImageBytes(ImageOptimizer.forPhoto(n.getImageBytes()));
                done++;
                logProgress(done, total);
            }
        }
        if (sats != null) {
            for (SatelliteImage s : sats) {
                if (s.getImageBytes() == null) {
                    continue;
                }
                s.setImageBytes(ImageOptimizer.forSatellite(s.getImageBytes()));
                done++;
                logProgress(done, total);
            }
        }
        log.info("Сжатие изображений завершено: {}/{}", done, total);
    }

    private static void logProgress(int done, int total) {
        if (done == total || done % 25 == 0) {
            log.info("Сжатие изображений: {}/{}", done, total);
        }
    }

    /**
     * Отмечает страницу раздела, если идёт черновой проход (pages != null).
     * В финальном проходе номера не нужны — содержание уже собрано.
     */
    private static void markSection(SectionPageRecorder pages, String sectionTitle,
            PdfWriter writer) {
        if (pages != null) {
            pages.mark(sectionTitle, writer);
        }
    }

    private void fillSections(Document document, PdfWriter writer, FieldPassport passport,
            SectionPageRecorder pages) {
        markSection(pages, SECTION_1, writer);
        document.add(PdfUIHelper.createSectionTitle(SECTION_1));

        document.add(PdfUIHelper.createParagraph(
                "1.1. Подразделение: " + PdfUIHelper.orNoData(passport.generalInfo().department())));
        document.add(PdfUIHelper.createParagraph(
                "1.2. Наименование: " + PdfUIHelper.orNoData(passport.generalInfo().fieldName())));
        document.add(PdfUIHelper.createParagraph("1.3. Текущая площадь: " + PdfUIHelper.formatArea(
                passport.generalInfo().fieldArea()) + " Га"));

        document.add(PdfUIHelper.createParagraph(
                "1.4. Севооборот в " + passport.generalInfo().year() + " году:"));

        var rotations = passport.generalInfo().rotations();
        if (rotations == null || rotations.isEmpty()) {
            document.add(PdfUIHelper.createParagraph(PdfUIHelper.NO_DATA));
        } else {
            // Подписанные строки без маркера: «Культура: …», «Сорт: …», «Репродукция: …».
            // Отсутствующие данные подставляются плейсхолдером orNoData, поэтому всегда
            // понятно, какого именно значения не хватает («Культура: — нет данных —»).
            for (var rotation : rotations) {
                document.add(PdfUIHelper.createRotationItem("Культура", rotation.crop()));
                document.add(PdfUIHelper.createRotationItem("Сорт", rotation.variety()));
                document.add(PdfUIHelper.createRotationItem("Репродукция", rotation.reproduction()));
            }
        }

        document.setPageSize(PageSize.A4.rotate());
        // Альбомные листы шире: шапка на всю ширину (842 pt) выше портретной,
        // поэтому верхнее поле увеличивается (действует со следующей страницы).
        document.setMargins(PdfUIHelper.MARGIN_LEFT, PdfUIHelper.MARGIN_RIGHT,
                PdfUIHelper.LANDSCAPE_MARGIN_TOP, PdfUIHelper.MARGIN_BOTTOM);
        document.newPage();
        markSection(pages, SECTION_2, writer);
        document.add(PdfUIHelper.createSectionTitle(SECTION_2));
        List<OperationTableRow> operationTableRows = passport.operations();
        document.add(PdfUIHelper.createOperationsTable(operationTableRows));

        PdfPTable tmcContainer = PdfUIHelper.createTmcContainer(operationTableRows);
        document.add(tmcContainer);

        document.newPage();
        markSection(pages, SECTION_3, writer);
        document.add(PdfUIHelper.createSectionTitle(SECTION_3));
        document.add(PdfUIHelper.createParagraph("3.1. Сводная информация"));
        List<NoteTableRow> noteTableRows = passport.notesSection().notes();
        PdfPTable notesTable = PdfUIHelper.createNotesTable(noteTableRows);
        document.add(notesTable);

        List<NoteImage> noteImages = passport.notesSection().images();
        Map<String, byte[]> photoMap = new LinkedHashMap<>();
        noteImages.stream()
                .sorted(NoteImageComparators.byComplexIndex())
                .forEach(img -> {
                    byte[] bytes = img.getImageBytes();
                    if (bytes != null) {
                        photoMap.put(img.getComplexIndex(), bytes);
                        log.debug("Добавлено фото: индекс={}, размер={} байт",
                                img.getComplexIndex(), bytes.length);
                    } else {
                        log.warn("Пустые данные для фото: индекс={}", img.getComplexIndex());
                    }
                });

        document.newPage();
        document.add(PdfUIHelper.createPhotoGrid(writer, photoMap));

        // --- Section 4. Spectral indices ---
        document.newPage();
        markSection(pages, SECTION_4, writer);
        ChartImage chartImage = passport.indexChart();
        PdfUIHelper.addChartImage(document, chartImage);

        // --- Section 5. Satellite Images ---
        document.newPage();
        markSection(pages, SECTION_5, writer);
        document.add(PdfUIHelper.createSectionTitle(SECTION_5));

        List<SatelliteImage> satelliteImages = passport.satelliteImages();

        if (satelliteImages == null || satelliteImages.isEmpty()) {
            document.add(PdfUIHelper.createParagraph(PdfUIHelper.NO_DATA));
        } else {
            PdfUIHelper.addSatelliteImagesColumn(document, satelliteImages);
        }

        // --- Section 6 ---
        document.newPage();
        markSection(pages, SECTION_6, writer);
        document.add(PdfUIHelper.createSectionTitle(SECTION_6));
        List<TechJournalTableRow> techJournalTableRows = passport.resources();
        PdfPTable techJournalTable = PdfUIHelper.createTechJournalTable(techJournalTableRows);
        document.add(techJournalTable);

        log.info("Данные поля успешно добавлены в документ");
    }

    /**
     * Черновой проход: рисует только разделы (без шапки и содержания) в память,
     * чтобы SectionPageRecorder собрал фактические номера страниц разделов.
     */
    private void renderSectionsToBytes(FieldPassport passport, SectionPageRecorder recorder)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedOutputStream bos = new BufferedOutputStream(baos);
                Document document = PdfUIHelper.createDocument()) {
            PdfWriter writer = PdfWriter.getInstance(document, bos);
            writer.setPageEvent(new PassportPageEventHandler());
            document.open();
            fillSections(document, writer, passport, recorder);
            log.debug("Черновой проход завершён. Страниц: {}", writer.getCurrentPageNumber());
        }
    }

    /**
     * Финальный проход: шапка «Паспорт поля», содержание и разделы. Номера страниц
     * в содержании берутся из чернового прохода со сдвигом на страницу содержания.
     */
    private void renderToTemp(Path tempPath, FieldPassport passport, SectionPageRecorder recorder)
            throws DocumentException, IOException {
        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                Document document = PdfUIHelper.createDocument()) {
            PdfWriter writer = PdfWriter.getInstance(document, bos);
            writer.setPageEvent(new PassportPageEventHandler());
            document.open();
            log.debug("PDF документ открыт (финальный проход)");

            document.add(PdfUIHelper.createPassportTitle());
            recorder.sectionTitles().forEach((title, page) ->
                    document.add(PdfUIHelper.createTocLine(title, page + TOC_PAGE_SHIFT)));
            document.newPage();

            fillSections(document, writer, passport, null);
        }
    }

    @Override
    public void generateAll(List<FieldPassport> passports) {
        if (passports == null || passports.isEmpty()) {
            log.warn("Список полей пуст, генерировать нечего.");
            return;
        }

        long startTime = System.currentTimeMillis();
        int total = passports.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        log.info("==> Начало массовой генерации (всего полей: {})", passports.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = passports.stream().map(p -> executor.submit(() -> {
                try {
                    generate(p);
                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    log.error("Generation failed: ", e);
                    return false;
                } finally {
                    int current = processedCount.incrementAndGet();
                    if (current % 50 == 0 || current == total) {
                        log.info("Прогресс: {}/{} документов готово.", current, total);
                    }
                }
            })).toList();

            for (Future<Boolean> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Поток завершился с критической ошибкой: {}",
                            e.getCause().getMessage());
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            log.info("==> Массовая генерация завершена! Время: {} мс. Успех {}/{}",
                    duration, successCount.get(), total);
        }
    }

    @Override
    public Path resolvePassportPath(FieldPassport passport) {
        String fieldName = passport.generalInfo().fieldName();
        String year = String.valueOf(passport.generalInfo().year());
        String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") + "_" + year + ".pdf";
        return this.outputDir.resolve(saveFileName);
    }

    /**
     * Запоминает номер страницы для каждого раздела во время чернового прохода.
     * Каждый раздел начинается с новой страницы (в {@code fillSections} перед ним
     * вызывается {@code newPage()}), поэтому отметка ставится сразу после
     * {@code newPage()} либо на первой странице перед «Разделом 1».
     */
    private static final class SectionPageRecorder {
        private final Map<String, Integer> pages = new LinkedHashMap<>();

        void mark(String sectionTitle, PdfWriter writer) {
            pages.putIfAbsent(sectionTitle, writer.getCurrentPageNumber());
        }

        /** Названия разделов в порядке следования с номерами страниц черновика. */
        Map<String, Integer> sectionTitles() {
            return new LinkedHashMap<>(pages);
        }
    }
}
