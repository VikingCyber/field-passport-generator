package com.viking.generator.mapper;

import com.viking.generator.data.dictionary.MachineDictionary;
import com.viking.generator.data.dto.RawOperationData;
import com.viking.generator.model.common.MachineResource;
import com.viking.generator.model.tables.TechJournalTableRow;

import java.util.Comparator;
import java.util.List;

import static com.viking.generator.util.StringUtils.clean;
import static com.viking.generator.util.StringUtils.cleanHtml;
import static com.viking.generator.util.StringUtils.normalize;

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
}
