package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 예산 정책 회귀 방지 단위 테스트 — 예산은 최상위(부모) 카테고리에만 설정 가능.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseBudgetServiceImplTest {

    @Mock private ExpenseBudgetRepository expenseBudgetRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ExpenseBudgetServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseBudgetServiceDto.CreateCommand command(Long categoryRowId) {
        return new ExpenseBudgetServiceDto.CreateCommand(USER_ID, categoryRowId, 300_000L, 2026, 6);
    }

    @Test
    @DisplayName("최상위 카테고리에는 예산을 설정할 수 있다")
    void createOnRootCategory() {
        User u = user(USER_ID);
        ExpenseCategory root = category(10L, u, null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(root));

        var info = sut.createBudget(command(10L));

        assertThat(info.categoryRowId()).isEqualTo(10L);
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("자식(하위) 카테고리에는 예산을 설정할 수 없다")
    void rejectOnChildCategory() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u, null);
        ExpenseCategory child = category(11L, u, parent);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(11L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> sut.createBudget(command(11L)))
                .isInstanceOf(InvalidValueException.class);
        verify(expenseBudgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 카테고리에는 예산을 설정할 수 없다")
    void rejectOnOthersCategory() {
        User u = user(USER_ID);
        User other = user(999L);
        ExpenseCategory othersCategory = category(20L, other, null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> sut.createBudget(command(20L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("카테고리 없는 전체 예산(category=null)은 설정할 수 있다")
    void createOverallBudget() {
        User u = user(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        var info = sut.createBudget(command(null));

        assertThat(info.categoryRowId()).isNull();
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
    }
}
