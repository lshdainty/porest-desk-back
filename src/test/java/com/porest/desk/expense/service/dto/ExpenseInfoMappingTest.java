package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExpenseInfo 매핑 안전성 테스트 — category 는 nullable(미분류 거래)이므로 null 이어도 NPE 없이 매핑돼야 한다.
 */
class ExpenseInfoMappingTest {

    @Test
    @DisplayName("from — 미분류(category=null) 거래도 NPE 없이 매핑된다")
    void mapsNullCategorySafely() {
        User u = User.createUser(null, "u", "테스터", "u@porest.com");
        ReflectionTestUtils.setField(u, "rowId", 1L);
        Expense e = Expense.createExpense(u, null, null, ExpenseType.EXPENSE, 10_000L,
                "미분류 지출", LocalDateTime.of(2026, 6, 15, 12, 0), "가게", "CARD", null, null);

        ExpenseServiceDto.ExpenseInfo info = ExpenseServiceDto.ExpenseInfo.from(e);

        assertThat(info.categoryRowId()).isNull();
        assertThat(info.categoryName()).isNull();
        assertThat(info.assetRowId()).isNull();
        assertThat(info.amount()).isEqualTo(10_000L);
    }
}
