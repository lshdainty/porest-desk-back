package com.porest.desk.expense.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseTemplateApiDto {

    public record CreateRequest(
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer sortOrder,
        YNType lockAmount
    ) {}

    public record UpdateRequest(
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        YNType lockAmount
    ) {}

    public record UseRequest(
        LocalDate expenseDate
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String templateName,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer useCount,
        Integer sortOrder,
        YNType lockAmount,
        LocalDateTime lastUsedAt,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseTemplateServiceDto.TemplateInfo info) {
            return new Response(
                info.rowId(), info.userRowId(), info.templateName(),
                info.categoryRowId(), info.categoryName(),
                info.assetRowId(), info.assetName(),
                info.expenseType(), info.amount(), info.description(),
                info.merchant(), info.paymentMethod(),
                info.useCount(), info.sortOrder(),
                info.lockAmount(), info.lastUsedAt(),
                info.createAt(), info.modifyAt()
            );
        }
    }

    public record ListResponse(List<Response> templates) {
        public static ListResponse from(List<ExpenseTemplateServiceDto.TemplateInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
