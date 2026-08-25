package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseSplitApiDto {

    public record SplitRequest(
        /** 기존 분할 행 아이디 — 보내면 제자리 수정된다. */
        Long rowId,
        Long categoryRowId,
        Long amount,
        String label,
        Integer sortOrder
    ) {}

    public record ReplaceRequest(
        List<SplitRequest> splits
    ) {}

    @Schema(name = "ExpenseSplitResponse")
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

    @Schema(name = "ExpenseSplitListResponse")
    public record ListResponse(List<Response> splits) {
        public static ListResponse from(List<ExpenseSplitServiceDto.SplitInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
