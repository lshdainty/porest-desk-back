package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 분할(ExpenseSplit) 정책 회귀 방지 단위 테스트 — 분할 합계는 거래 금액과 일치해야 하고,
 * 각 분할 카테고리는 leaf(자식 없는)여야 하며, 거래 소유자만 교체할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseSplitServiceImplTest {

    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;

    @InjectMocks private ExpenseSplitServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseSplitServiceDto.ReplaceCommand replaceCmd(List<ExpenseSplitServiceDto.SplitCommand> splits) {
        return new ExpenseSplitServiceDto.ReplaceCommand(5L, USER_ID, splits);
    }

    @Test
    @DisplayName("replaceSplits — 분할 합계가 거래 금액과 다르면 거부")
    void rejectAmountMismatch() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        given(expense.getAmount()).willReturn(10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(10L, 5_000L, "절반", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("replaceSplits — 자식 보유(상위) 카테고리 분할은 거부")
    void rejectNonLeafCategory() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        given(expense.getAmount()).willReturn(10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        ExpenseCategory parent = category(10L, u);
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(10L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("replaceSplits — 남의 거래는 분할 교체 불가")
    void rejectOthersExpense() {
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(user(999L));
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(10L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(ForbiddenException.class);
    }
}
