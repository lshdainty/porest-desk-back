package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseSplit;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseSplitServiceDto {

    public record SplitCommand(
        /**
         * 기존 분할 행 아이디 — 있으면 제자리 수정, 없으면 신규.
         * 지금은 분할에 상태 컬럼이 없어 잃는 게 없지만, 붙는 순간 통째 교체가 그걸 날린다.
         */
        Long rowId,
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
