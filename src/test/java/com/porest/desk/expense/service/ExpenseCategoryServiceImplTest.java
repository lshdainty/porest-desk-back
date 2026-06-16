package com.porest.desk.expense.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 카테고리·예산 정책 로직 회귀 방지 단위 테스트.
 *
 * <p>repository 는 모두 mock — DB·컨텍스트 없이 {@link ExpenseCategoryServiceImpl} 의
 * 비즈니스 규칙(부모/자식 계층, 거래 보유 제약, 예산 cascade 삭제)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceImplTest {

    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseBudgetRepository expenseBudgetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ExpenseCategoryServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner, ExpenseCategory parent, ExpenseType type) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "utensils", "#fff", type, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    @Nested
    @DisplayName("createCategory")
    class CreateCategory {

        @Test
        @DisplayName("최상위 카테고리는 정상 생성된다")
        void createTopLevel() {
            User u = user(USER_ID);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "식비", "utensils", "#fff", ExpenseType.EXPENSE, null);

            var info = sut.createCategory(command);

            assertThat(info.categoryName()).isEqualTo("식비");
            assertThat(info.expenseType()).isEqualTo(ExpenseType.EXPENSE);
            assertThat(info.parentRowId()).isNull();
            verify(expenseCategoryRepository).save(any(ExpenseCategory.class));
        }

        @Test
        @DisplayName("부모가 이미 자식인 경우(2단계 초과) 생성 불가")
        void rejectWhenParentIsChild() {
            User u = user(USER_ID);
            ExpenseCategory grandParent = category(10L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory parentAlreadyChild = category(11L, u, grandParent, ExpenseType.EXPENSE);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(expenseCategoryRepository.findById(11L)).willReturn(Optional.of(parentAlreadyChild));

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "외식", "utensils", "#fff", ExpenseType.EXPENSE, 11L);

            assertThatThrownBy(() -> sut.createCategory(command))
                    .isInstanceOf(InvalidValueException.class);
            verify(expenseCategoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("부모와 구분(expenseType)이 다르면 생성 불가")
        void rejectWhenTypeMismatch() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(20L, u, null, ExpenseType.EXPENSE);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(parent));

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "부수입", "utensils", "#fff", ExpenseType.INCOME, 20L);

            assertThatThrownBy(() -> sut.createCategory(command))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("거래가 있는 카테고리는 부모가 될 수 없다")
        void rejectWhenParentHasTransactions() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(30L, u, null, ExpenseType.EXPENSE);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(expenseCategoryRepository.findById(30L)).willReturn(Optional.of(parent));
            given(expenseRepository.existsByCategory(30L)).willReturn(true);

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "외식", "utensils", "#fff", ExpenseType.EXPENSE, 30L);

            assertThatThrownBy(() -> sut.createCategory(command))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategory {

        @Test
        @DisplayName("최상위 → 하위(강등)는 금지")
        void rejectDemote() {
            User u = user(USER_ID);
            ExpenseCategory topLevel = category(40L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory otherParent = category(41L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(40L)).willReturn(Optional.of(topLevel));

            var command = new ExpenseCategoryServiceDto.UpdateCommand(
                    "식비", "utensils", "#fff", ExpenseType.EXPENSE, 0, otherParent.getRowId());

            assertThatThrownBy(() -> sut.updateCategory(40L, USER_ID, command))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("하위 → 최상위(승격)는 금지")
        void rejectPromote() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(50L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory child = category(51L, u, parent, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(51L)).willReturn(Optional.of(child));

            var command = new ExpenseCategoryServiceDto.UpdateCommand(
                    "외식", "utensils", "#fff", ExpenseType.EXPENSE, 0, null);

            assertThatThrownBy(() -> sut.updateCategory(51L, USER_ID, command))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("하위 → 다른 하위(부모 이동)는 허용")
        void allowMoveToAnotherParent() {
            User u = user(USER_ID);
            ExpenseCategory oldParent = category(60L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory child = category(61L, u, oldParent, ExpenseType.EXPENSE);
            ExpenseCategory newParent = category(62L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(61L)).willReturn(Optional.of(child));
            given(expenseCategoryRepository.findById(62L)).willReturn(Optional.of(newParent));
            given(expenseCategoryRepository.hasChildren(62L)).willReturn(true);

            var command = new ExpenseCategoryServiceDto.UpdateCommand(
                    "외식", "utensils", "#fff", ExpenseType.EXPENSE, 0, 62L);

            var info = sut.updateCategory(61L, USER_ID, command);

            assertThat(info.parentRowId()).isEqualTo(62L);
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategory {

        @Test
        @DisplayName("자식이 있는 부모는 삭제 불가")
        void rejectDeleteWithChildren() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(70L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(70L)).willReturn(Optional.of(parent));
            given(expenseCategoryRepository.hasChildren(70L)).willReturn(true);

            assertThatThrownBy(() -> sut.deleteCategory(70L, USER_ID))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("예산이 걸린 카테고리는 삭제 시 예산도 함께 삭제된다")
        void cascadeDeleteBudgets() {
            User u = user(USER_ID);
            ExpenseCategory leaf = category(80L, u, null, ExpenseType.EXPENSE);
            ExpenseBudget budget = org.mockito.Mockito.mock(ExpenseBudget.class);
            given(expenseCategoryRepository.findById(80L)).willReturn(Optional.of(leaf));
            given(expenseCategoryRepository.hasChildren(80L)).willReturn(false);
            given(expenseBudgetRepository.findAllByCategory(80L)).willReturn(List.of(budget));

            sut.deleteCategory(80L, USER_ID);

            verify(expenseBudgetRepository, times(1)).delete(budget);
        }
    }

    @Nested
    @DisplayName("reorderCategories")
    class ReorderCategory {

        @Test
        @DisplayName("같은 부모 내 순서 변경은 sortOrder 만 갱신")
        void sameParentReorderUpdatesSortOrder() {
            User u = user(USER_ID);
            ExpenseCategory cat = category(10L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(cat));

            sut.reorderCategories(USER_ID, List.of(
                    new ExpenseCategoryServiceDto.ReorderItem(10L, 3, null)));

            assertThat(cat.getSortOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("자기 자신을 부모로 지정하면 불가")
        void rejectSelfAsParent() {
            User u = user(USER_ID);
            ExpenseCategory cat = category(10L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(cat));

            assertThatThrownBy(() -> sut.reorderCategories(USER_ID, List.of(
                    new ExpenseCategoryServiceDto.ReorderItem(10L, 0, 10L))))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("이미 부모가 있는(2단계) 카테고리 아래로는 이동 불가")
        void rejectDepthOverflow() {
            User u = user(USER_ID);
            ExpenseCategory grandParent = category(1L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory parentWithParent = category(2L, u, grandParent, ExpenseType.EXPENSE);
            ExpenseCategory cat = category(10L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(cat));
            given(expenseCategoryRepository.findById(2L)).willReturn(Optional.of(parentWithParent));

            assertThatThrownBy(() -> sut.reorderCategories(USER_ID, List.of(
                    new ExpenseCategoryServiceDto.ReorderItem(10L, 0, 2L))))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("구분(expenseType)이 다른 부모로는 이동 불가")
        void rejectTypeMismatch() {
            User u = user(USER_ID);
            ExpenseCategory incomeParent = category(2L, u, null, ExpenseType.INCOME);
            ExpenseCategory cat = category(10L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(cat));
            given(expenseCategoryRepository.findById(2L)).willReturn(Optional.of(incomeParent));

            assertThatThrownBy(() -> sut.reorderCategories(USER_ID, List.of(
                    new ExpenseCategoryServiceDto.ReorderItem(10L, 0, 2L))))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
