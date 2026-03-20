package com.porest.desk.calendar.service.dto;

import com.porest.desk.calendar.domain.EventLabel;

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
        Integer sortOrder
    ) {
        public static LabelInfo from(EventLabel label) {
            return new LabelInfo(
                label.getRowId(),
                label.getUser().getRowId(),
                label.getLabelName(),
                label.getColor(),
                label.getSortOrder()
            );
        }
    }
}
