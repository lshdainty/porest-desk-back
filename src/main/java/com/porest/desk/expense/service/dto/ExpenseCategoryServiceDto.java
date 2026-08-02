package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;

public class ExpenseCategoryServiceDto {

    /** 일괄 카테고리 이동 결과 — 무엇이 몇 건 옮겨졌는지. */
    public record MoveResult(int expenses, int recurring, int splits) {}

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
        // null 이면 기존 유지
        ExpenseType expenseType,
        Integer sortOrder,
        // null = 최상위로 이동 (웹/앱 편집 다이얼로그는 항상 포함 전송)
        Long parentRowId
    ) {}

    public record ReorderItem(
        Long categoryRowId,
        Integer sortOrder,
        Long parentRowId
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
