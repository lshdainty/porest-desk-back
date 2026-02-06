package com.porest.desk.calculator.controller.dto;

import com.porest.desk.calculator.service.dto.CalculatorHistoryServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class CalculatorHistoryApiDto {

    public record CreateRequest(
        String expression,
        String result
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String expression,
        String result,
        LocalDateTime createAt
    ) {
        public static Response from(CalculatorHistoryServiceDto.HistoryInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.expression(),
                info.result(),
                info.createAt()
            );
        }
    }

    public record ListResponse(
        List<Response> histories
    ) {
        public static ListResponse from(List<CalculatorHistoryServiceDto.HistoryInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
