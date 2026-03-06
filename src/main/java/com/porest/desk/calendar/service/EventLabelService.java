package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.EventLabelServiceDto;

import java.util.List;

public interface EventLabelService {
    EventLabelServiceDto.LabelInfo createLabel(EventLabelServiceDto.CreateCommand command);
    List<EventLabelServiceDto.LabelInfo> getLabels(Long userRowId);
    EventLabelServiceDto.LabelInfo updateLabel(Long labelId, Long userRowId, EventLabelServiceDto.UpdateCommand command);
    void deleteLabel(Long labelId, Long userRowId);
}
