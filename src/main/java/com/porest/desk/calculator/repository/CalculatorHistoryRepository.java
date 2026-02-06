package com.porest.desk.calculator.repository;

import com.porest.desk.calculator.domain.CalculatorHistory;

import java.util.List;

public interface CalculatorHistoryRepository {
    List<CalculatorHistory> findAllByUser(Long userRowId);
    CalculatorHistory save(CalculatorHistory history);
    void deleteAllByUser(Long userRowId);
}
