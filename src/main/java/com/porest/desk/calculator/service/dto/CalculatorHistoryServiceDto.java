package com.porest.desk.calculator.service.dto;

import com.porest.desk.calculator.domain.CalculatorHistory;

import java.time.LocalDateTime;

public class CalculatorHistoryServiceDto {

    public record CreateCommand(
        Long userRowId,
        String expression,
        String result
    ) {}

    public record HistoryInfo(
        Long rowId,
        Long userRowId,
        String expression,
        String result,
        LocalDateTime createAt
    ) {
        public static HistoryInfo from(CalculatorHistory history) {
            return new HistoryInfo(
                history.getRowId(),
                history.getUser().getRowId(),
                history.getExpression(),
                history.getResult(),
                history.getCreateAt()
            );
        }
    }
}
