package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.data.dictionary.MachineDictionary;
import com.viking.field_passport_generator.data.dto.RawOperationData;
import com.viking.field_passport_generator.model.MachineResource;
import com.viking.field_passport_generator.model.TechJournalTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.Comparator;
import java.util.List;

import static com.viking.field_passport_generator.util.StringUtils.clean;
import static com.viking.field_passport_generator.util.StringUtils.normalize;

public class TechJournalMapper {
    private final String emptyLabel;

    public TechJournalMapper(String emptyLabel) {
        this.emptyLabel = emptyLabel;
    }

    public List<TechJournalTableRow> mapToTableRow(List<RawOperationData> cleanOps,
                                                   MachineDictionary machineDict){

        if (cleanOps == null || cleanOps.isEmpty()) return List.of();

        return cleanOps.stream()
                .sorted(Comparator.comparing(RawOperationData::getMTime, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(op -> {
                            MachineResource machine = machineDict.getById(op.getUnitId());
                            String fieldTool = clean(op.getFieldTool());
                            String cleanDate = normalize(cleanHtml(op.getDate()), emptyLabel);
                            String cleanDriver = normalize(op.getDriver(), emptyLabel);

                            return new TechJournalTableRow(
                                    machine,
                                    fieldTool,
                                    cleanDriver,
                                    cleanDate
                            );
                        }).toList();
    }

    private String cleanHtml(String html) {
        if (html == null || html.isBlank()) return "";
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element br : doc.select("br")) {
            br.replaceWith(new TextNode(" "));
        }
        return doc.body().text().trim();
    }
}
