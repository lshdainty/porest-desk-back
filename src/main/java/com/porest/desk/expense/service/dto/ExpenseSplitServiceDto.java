package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseSplit;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseSplitServiceDto {

    public record SplitCommand(
        Long categoryRowId,
        Long amount,
        String label,
        Integer sortOrder
    ) {}

    public record ReplaceCommand(
        Long expenseRowId,
        Long userRowId,
        List<SplitCommand> splits
    ) {}

    public record SplitInfo(
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
        public static SplitInfo from(ExpenseSplit split) {
            return new SplitInfo(
                split.getRowId(),
                split.getExpense().getRowId(),
                split.getCategory().getRowId(),
                split.getCategory().getCategoryName(),
                split.getAmount(),
                split.getLabel(),
                split.getSortOrder(),
                split.getCreateAt(),
                split.getModifyAt()
            );
        }
    }
}
