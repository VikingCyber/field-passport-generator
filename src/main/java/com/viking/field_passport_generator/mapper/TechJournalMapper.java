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

public class TechJournalMapper {

    public List<TechJournalTableRow> mapToTableRow(List<RawOperationData> cleanOps,
                                                   MachineDictionary machineDict){

        if (cleanOps == null || cleanOps.isEmpty()) return List.of();

        return cleanOps.stream()
                .sorted(Comparator.comparing(RawOperationData::getmTime))
                        .map(op -> {
                            MachineResource machine = machineDict.getById(op.getUnitId());
                            String fieldTool = op.getFieldTool();
                            String cleanDate = cleanHtml(op.getDate());

                            return new TechJournalTableRow(
                                    machine,
                                    fieldTool,
                                    op.getDriver() != null ? op.getDriver() : "Не указан",
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
