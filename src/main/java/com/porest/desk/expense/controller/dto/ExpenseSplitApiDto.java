package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseSplitApiDto {

    public record SplitRequest(
        Long categoryRowId,
        Long amount,
        String label,
        Integer sortOrder
    ) {}

    public record ReplaceRequest(
        List<SplitRequest> splits
    ) {}

    public record Response(
        Long rowId,
        Long expenseRowId,
        Long categoryRowId,
        String categoryName,
        Long amount,
        String label,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseSplitServiceDto.SplitInfo info) {
            return new Response(
                info.rowId(),
                info.expenseRowId(),
                info.categoryRowId(),
                info.categoryName(),
                info.amount(),
                info.label(),
                info.sortOrder(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(List<Response> splits) {
        public static ListResponse from(List<ExpenseSplitServiceDto.SplitInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
