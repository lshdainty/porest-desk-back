package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseCategoryApiDto {

    public record CreateRequest(
        String categoryName,
        String icon,
        String color,
        ExpenseType expenseType,
        Long parentRowId
    ) {}

    public record UpdateRequest(
        String categoryName,
        String icon,
        String color,
        Integer sortOrder
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String categoryName,
        String icon,
        String color,
        ExpenseType expenseType,
        Integer sortOrder,
        Long parentRowId,
        boolean hasChildren,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseCategoryServiceDto.CategoryInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryName(),
                info.icon(),
                info.color(),
                info.expenseType(),
                info.sortOrder(),
                info.parentRowId(),
                info.hasChildren(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> categories
    ) {
        public static ListResponse from(List<ExpenseCategoryServiceDto.CategoryInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }

    public record ReorderItem(
        Long categoryRowId,
        Integer sortOrder,
        Long parentRowId
    ) {}

    public record ReorderRequest(List<ReorderItem> items) {}
}
