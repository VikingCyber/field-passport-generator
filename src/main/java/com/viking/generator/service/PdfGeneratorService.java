package com.viking.generator.service;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.NoteImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.util.FileUtils;
import com.viking.generator.util.ImageOptimizer;
import com.viking.generator.util.PassportPageEventHandler;
import com.viking.generator.util.StringUtils;
import com.viking.generator.util.pdf.PdfSection1GeneralInfo;
import com.viking.generator.util.pdf.PdfSection2Operations;
import com.viking.generator.util.pdf.PdfSection3Notes;
import com.viking.generator.util.pdf.PdfSection4Indexes;
import com.viking.generator.util.pdf.PdfSection5Satellites;
import com.viking.generator.util.pdf.PdfSection6Resources;
import com.viking.generator.util.pdf.PdfSectionFieldMap;
import com.viking.generator.util.pdf.PdfSectionRenderer;
import com.viking.generator.util.pdf.PdfToc;
import com.viking.generator.util.pdf.PdfTypography;
import com.viking.generator.util.pdf.SectionPageRecorder;
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
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PdfGeneratorService implements PassportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    /**
     * Компоненты разделов в порядке следования. Каждый раздел — отдельный класс,
     * который сам управляет своей страницей и отметкой в рекордере; добавление
     * нового раздела сводится к внесению класса сюда.
     */
    private static final List<PdfSectionRenderer> SECTIONS = List.of(
            new PdfSection1GeneralInfo(),
            new PdfSectionFieldMap(),
            new PdfSection2Operations(),
            new PdfSection3Notes(),
            new PdfSection4Indexes(),
            new PdfSection5Satellites(),
            new PdfSection6Resources());

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

            // Pass 1 — черновик в память: весь макет (содержание + разделы), чтобы
            // SectionPageRecorder собрал АБСОЛЮТНЫЕ номера страниц разделов: черновик
            // занимает столько же места, сколько финальный файл, поэтому сдвига нет.
            SectionPageRecorder recorder = new SectionPageRecorder();
            log.info("Проход 1/2: черновой рендер макета (сбор содержания)...");
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

    /** Рисует все разделы по списку компонентов; каждый сам управляет своей страницей. */
    private void fillSections(Document document, PdfWriter writer, FieldPassport passport,
            SectionPageRecorder pages) throws DocumentException {
        for (PdfSectionRenderer section : SECTIONS) {
            section.render(document, writer, pages, passport);
        }
        log.info("Данные поля успешно добавлены в документ");
    }

    /**
     * Черновой проход: рисует шапку, содержание и разделы в память. Содержание
     * занимает ровно столько места, сколько в финале (номера-заглушки не меняют
     * высоту строк), поэтому рекордер собирает уже абсолютные номера страниц.
     */
    private void renderSectionsToBytes(FieldPassport passport, SectionPageRecorder recorder)
            throws DocumentException, IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedOutputStream bos = new BufferedOutputStream(baos);
                Document document = PdfTypography.createDocument()) {
            PdfWriter writer = PdfWriter.getInstance(document, bos);
            writer.setPageEvent(new PassportPageEventHandler());
            document.open();
            addFrontMatter(document, placeholderPages(), null);
            fillSections(document, writer, passport, recorder);
            log.debug("Черновой проход завершён. Страниц: {}", writer.getCurrentPageNumber());
        }
    }

    /**
     * Шапка и содержание. Вызывается в обоих проходах, поэтому черновик занимает
     * столько же места, сколько финальный файл, и номера разделов из черновика
     * корректны без сдвига. После содержания — принудительный разрыв страницы:
     * раздел 1 всегда начинается с новой страницы. Если передан writer — строки
     * содержания делаются кликабельными (внутренние ссылки на страницы разделов),
     * иначе (черновик) ссылки не создаются, т.к. номера ещё заглушечные.
     */
    private static void addFrontMatter(Document document, Map<String, Integer> pages,
            PdfWriter writer) throws DocumentException {
        document.add(PdfTypography.createPassportTitle());
        pages.forEach((title, page) -> document.add(
                PdfToc.createTocLine(title, page, writer)));
        document.newPage();
    }

    /**
     * Заглушка для чернового прохода: те же строки содержания, но с нулевыми номерами.
     * Ширина номера не влияет на высоту строки содержания (одна строка, ширина колонки
     * не меньше минимума), поэтому высота заглушки идентична финальной.
     */
    private static Map<String, Integer> placeholderPages() {
        Map<String, Integer> pages = new LinkedHashMap<>();
        for (PdfSectionRenderer section : SECTIONS) {
            if (section.inToc()) {
                pages.put(section.title(), 0);
            }
        }
        return pages;
    }

    /**
     * Финальный проход: шапка «Паспорт поля», содержание и разделы. Номера страниц
     * в содержании берутся из чернового прохода: поскольку черновик содержит то же
     * содержание, они уже абсолютные.
     */
    private void renderToTemp(Path tempPath, FieldPassport passport, SectionPageRecorder recorder)
            throws DocumentException, IOException {
        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                Document document = PdfTypography.createDocument()) {
            PdfWriter writer = PdfWriter.getInstance(document, bos);
            writer.setPageEvent(new PassportPageEventHandler());
            document.open();
            log.debug("PDF документ открыт (финальный проход)");

            addFrontMatter(document, recorder.sectionTitles(), writer);
            fillSections(document, writer, passport, null);
        }
    }

    @Override
    public Path resolvePassportPath(FieldPassport passport) {
        String fieldName = passport.generalInfo().fieldName();
        String year = String.valueOf(passport.generalInfo().year());
        String saveFileName = StringUtils.sanitizeFileName(fieldName) + "_" + year + ".pdf";
        return this.outputDir.resolve(saveFileName);
    }
}