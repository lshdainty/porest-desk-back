package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;

public class ExpenseCategoryServiceDto {

    public record CreateCommand(
        Long userRowId,
        String categoryName,
        String icon,
        String color,
        ExpenseType expenseType,
        Long parentRowId
    ) {}

    public record UpdateCommand(
        String categoryName,
        String icon,
        String color,
        Integer sortOrder
    ) {}

    public record CategoryInfo(
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
        public static CategoryInfo from(ExpenseCategory category) {
            return new CategoryInfo(
                category.getRowId(),
                category.getUser().getRowId(),
                category.getCategoryName(),
                category.getIcon(),
                category.getColor(),
                category.getExpenseType(),
                category.getSortOrder(),
                category.getParent() != null ? category.getParent().getRowId() : null,
                false,
                category.getCreateAt(),
                category.getModifyAt()
            );
        }

        public static CategoryInfo fromWithHasChildren(ExpenseCategory category, boolean hasChildren) {
            return new CategoryInfo(
                category.getRowId(),
                category.getUser().getRowId(),
                category.getCategoryName(),
                category.getIcon(),
                category.getColor(),
                category.getExpenseType(),
                category.getSortOrder(),
                category.getParent() != null ? category.getParent().getRowId() : null,
                hasChildren,
                category.getCreateAt(),
                category.getModifyAt()
            );
        }
    }
}
