package com.porest.desk.calculator.service;

import com.porest.desk.calculator.service.dto.CalculatorHistoryServiceDto;

import java.util.List;

public interface CalculatorHistoryService {
    CalculatorHistoryServiceDto.HistoryInfo createHistory(CalculatorHistoryServiceDto.CreateCommand command);
    List<CalculatorHistoryServiceDto.HistoryInfo> getHistories(Long userRowId);
    void deleteAllHistories(Long userRowId);
}
