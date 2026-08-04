package com.porest.desk.expense.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import java.time.LocalDateTime;
import com.porest.desk.expense.domain.Expense;

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
    @Mock private ExpenseSplitRepository expenseSplitRepository;
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
        @DisplayName("같은 위치·같은 타입에 같은 이름의 활성 카테고리가 있으면 생성 불가(중복 방지)")
        void rejectDuplicateActiveName() {
            User u = user(USER_ID);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(expenseCategoryRepository.existsActiveByUserAndParentAndTypeAndName(
                    USER_ID, null, ExpenseType.EXPENSE, "식비", null)).willReturn(true);

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "식비", "utensils", "#fff", ExpenseType.EXPENSE, null);

            assertThatThrownBy(() -> sut.createCategory(command))
                    .isInstanceOf(InvalidValueException.class);
            verify(expenseCategoryRepository, never()).save(any());
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

        @Test
        @DisplayName("분할(split)에만 쓰인 카테고리도 부모가 될 수 없다")
        void rejectWhenParentHasSplits() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(31L, u, null, ExpenseType.EXPENSE);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(expenseCategoryRepository.findById(31L)).willReturn(Optional.of(parent));
            given(expenseRepository.existsByCategory(31L)).willReturn(false);
            given(recurringTransactionRepository.existsByCategory(31L)).willReturn(false);
            given(expenseSplitRepository.existsActiveByCategory(31L)).willReturn(true);

            var command = new ExpenseCategoryServiceDto.CreateCommand(
                    USER_ID, "외식", "utensils", "#fff", ExpenseType.EXPENSE, 31L);

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

    @Nested
    @DisplayName("moveTransactions — 카테고리에 달린 거래를 다른 카테고리로 일괄 이동")
    class MoveTransactions {

        @Test
        @DisplayName("거래·반복거래·분할을 모두 옮긴다 — 셋 다 옮겨야 부모가 될 수 있다")
        void movesAllReferences() {
            User u = user(USER_ID);
            ExpenseCategory src = category(10L, u, null, ExpenseType.EXPENSE);
            ExpenseCategory dst = category(11L, u, null, ExpenseType.EXPENSE);
            Expense e = Expense.createExpense(u, src, null, ExpenseType.EXPENSE, 1000L,
                null, LocalDateTime.of(2026, 5, 1, 0, 0), null, null, null, null);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(src));
            given(expenseCategoryRepository.findById(11L)).willReturn(Optional.of(dst));
            given(expenseCategoryRepository.hasChildren(11L)).willReturn(false);
            given(expenseRepository.findActiveByCategory(10L)).willReturn(List.of(e));
            given(recurringTransactionRepository.findActiveByCategory(10L)).willReturn(List.of());
            given(expenseSplitRepository.findActiveByCategory(10L)).willReturn(List.of());

            var moved = sut.moveTransactions(10L, 11L, USER_ID);

            assertThat(moved.expenses()).isEqualTo(1);
            assertThat(e.getCategory()).isSameAs(dst);
        }

        @Test
        @DisplayName("대상이 자식을 가진 부모면 거부 — 거래는 말단에만 달 수 있다")
        void rejectsWhenTargetIsParent() {
            User u = user(USER_ID);
            given(expenseCategoryRepository.findById(10L))
                .willReturn(Optional.of(category(10L, u, null, ExpenseType.EXPENSE)));
            given(expenseCategoryRepository.findById(11L))
                .willReturn(Optional.of(category(11L, u, null, ExpenseType.EXPENSE)));
            given(expenseCategoryRepository.hasChildren(11L)).willReturn(true);

            assertThatThrownBy(() -> sut.moveTransactions(10L, 11L, USER_ID))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("유형이 다르면 거부 — 지출 거래를 수입 카테고리로 옮길 수 없다")
        void rejectsTypeMismatch() {
            User u = user(USER_ID);
            given(expenseCategoryRepository.findById(10L))
                .willReturn(Optional.of(category(10L, u, null, ExpenseType.EXPENSE)));
            given(expenseCategoryRepository.findById(11L))
                .willReturn(Optional.of(category(11L, u, null, ExpenseType.INCOME)));

            assertThatThrownBy(() -> sut.moveTransactions(10L, 11L, USER_ID))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("같은 카테고리로는 옮길 수 없다")
        void rejectsSameCategory() {
            assertThatThrownBy(() -> sut.moveTransactions(10L, 10L, USER_ID))
                .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("moveTransactionsToNewChild — 하위를 만들면서 거래를 그리로 옮긴다")
    class MoveToNewChild {

        @Test
        @DisplayName("거래가 있어 하위를 못 만들던 교착을 푼다 — 생성과 이동이 한 트랜잭션")
        void createsChildAndMovesTransactions() {
            User u = user(USER_ID);
            ExpenseCategory src = category(10L, u, null, ExpenseType.EXPENSE);
            Expense e = Expense.createExpense(u, src, null, ExpenseType.EXPENSE, 1000L,
                null, LocalDateTime.of(2026, 5, 1, 0, 0), null, null, null, null);
            given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(src));
            given(expenseCategoryRepository.existsActiveByUserAndParentAndTypeAndName(
                eq(USER_ID), eq(10L), any(), eq("강의"), isNull())).willReturn(false);
            given(expenseRepository.findActiveByCategory(10L)).willReturn(List.of(e));
            given(recurringTransactionRepository.findActiveByCategory(10L)).willReturn(List.of());
            given(expenseSplitRepository.findActiveByCategory(10L)).willReturn(List.of());

            var moved = sut.moveTransactionsToNewChild(10L, "강의", "book", "#111", USER_ID);

            assertThat(moved.expenses()).isEqualTo(1);
            // 거래가 새 자식으로 옮겨져야 원래 카테고리가 부모 자격을 얻는다.
            assertThat(e.getCategory().getCategoryName()).isEqualTo("강의");
            assertThat(e.getCategory().getParent()).isSameAs(src);
            verify(expenseCategoryRepository).save(any(ExpenseCategory.class));
        }

        @Test
        @DisplayName("이미 자식이 있는 카테고리에는 이 경로를 쓰지 않는다 — 일반 이동을 쓰면 된다")
        void rejectsWhenAlreadyHasChildren() {
            User u = user(USER_ID);
            given(expenseCategoryRepository.findById(10L))
                .willReturn(Optional.of(category(10L, u, null, ExpenseType.EXPENSE)));
            given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

            assertThatThrownBy(() -> sut.moveTransactionsToNewChild(10L, "강의", "book", "#111", USER_ID))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("하위 카테고리에는 또 하위를 만들 수 없다(최대 2단계)")
        void rejectsWhenSourceIsChild() {
            User u = user(USER_ID);
            ExpenseCategory parent = category(9L, u, null, ExpenseType.EXPENSE);
            given(expenseCategoryRepository.findById(10L))
                .willReturn(Optional.of(category(10L, u, parent, ExpenseType.EXPENSE)));

            assertThatThrownBy(() -> sut.moveTransactionsToNewChild(10L, "강의", "book", "#111", USER_ID))
                .isInstanceOf(InvalidValueException.class);
        }
    }
}
