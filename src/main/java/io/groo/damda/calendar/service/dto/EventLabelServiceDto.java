package io.groo.damda.calendar.service.dto;

import io.groo.damda.calendar.domain.EventLabel;

public class EventLabelServiceDto {

    public record CreateCommand(
        Long userRowId,
        String labelName,
        String color
    ) {}

    public record UpdateCommand(
        String labelName,
        String color
    ) {}

    public record LabelInfo(
        Long rowId,
        Long userRowId,
        String labelName,
        String color,
        Integer sortOrder,
        long usageCount
    ) {
        public static LabelInfo from(EventLabel label) {
            return from(label, 0L);
        }

        public static LabelInfo from(EventLabel label, long usageCount) {
            return new LabelInfo(
                label.getRowId(),
                label.getUser().getRowId(),
                label.getLabelName(),
                label.getColor(),
                label.getSortOrder(),
                usageCount
            );
        }
    }
}
