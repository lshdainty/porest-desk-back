package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.Expense;
import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
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

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 5_000L, "절반", 0));

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
        given(expense.getExpenseType()).willReturn(ExpenseType.EXPENSE); // 유형 검증 통과 후 leaf 검증까지 도달
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        ExpenseCategory parent = category(10L, u); // category() 는 EXPENSE 타입
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("replaceSplits — 분할 카테고리 유형이 거래 유형과 다르면 거부(타입 일치 강제)")
    void rejectTypeMismatch() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        given(expense.getAmount()).willReturn(10_000L);
        given(expense.getExpenseType()).willReturn(ExpenseType.EXPENSE);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        // INCOME 타입 카테고리를 EXPENSE 거래의 분할에 지정 → 거부
        ExpenseCategory incomeCat = ExpenseCategory.createCategory(u, "급여", "tag", "#fff", ExpenseType.INCOME, null);
        ReflectionTestUtils.setField(incomeCat, "rowId", 30L);
        given(expenseCategoryRepository.findById(30L)).willReturn(Optional.of(incomeCat));

        // 합계는 거래 금액과 일치(타입 검증 분기까지 도달하도록)
        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(
            null,30L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("replaceSplits — 남의 거래는 분할 교체 불가")
    void rejectOthersExpense() {
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(user(999L));
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("replaceSplits — 남의 카테고리로 분할 지정 불가(소유권 검증 누락 보강)")
    void rejectOthersCategory() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        given(expense.getAmount()).willReturn(10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        ExpenseCategory othersCategory = category(20L, user(999L));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        var splits = List.of(new ExpenseSplitServiceDto.SplitCommand(
            null,20L, 10_000L, "전부", 0));

        assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Nested
    @DisplayName("분할 금액 부호 — 합계만 맞으면 통과하던 구멍")
    class SplitAmountSign {

        /** 개별 금액 검증이 합계 검사보다 먼저 도므로 getAmount 는 스텁하지 않는다. */
        private Expense expenseOf(long amount) {
            User u = user(USER_ID);
            Expense expense = mock(Expense.class);
            given(expense.getUser()).willReturn(u);
            lenient().when(expense.getAmount()).thenReturn(amount);
            given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
            return expense;
        }

        @Test
        @DisplayName("음수 분할 — 합계가 맞아도 거부한다")
        void rejectsNegativeSplit() {
            expenseOf(10_000L);
            // +30,000 − 20,000 = 10,000. 합계 검사만 하면 통과해 버리고,
            // 카테고리 통계에 −20,000 짜리 항목이 박힌다.
            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 30_000L, "식사", 0),
                new ExpenseSplitServiceDto.SplitCommand(
            null,11L, -20_000L, "할인", 1));

            assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("0원 분할 — 아무것도 안 담는 항목은 거부한다")
        void rejectsZeroSplit() {
            expenseOf(10_000L);
            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 10_000L, "전부", 0),
                new ExpenseSplitServiceDto.SplitCommand(
            null,11L, 0L, "빈 항목", 1));

            assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("금액이 없는 분할 — NPE 대신 검증 오류로 막는다")
        void rejectsNullSplitAmount() {
            expenseOf(10_000L);
            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(
            null,10L, null, "빠뜨림", 0));

            assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("정상 분할은 그대로 통과한다 — 7,000 + 3,000 = 10,000")
        void acceptsValidSplits() {
            User u = user(USER_ID);
            Expense expense = mock(Expense.class);
            given(expense.getUser()).willReturn(u);
            given(expense.getAmount()).willReturn(10_000L);
            given(expense.getExpenseType()).willReturn(ExpenseType.EXPENSE);
            given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
            given(expenseCategoryRepository.findById(anyLong()))
                .willAnswer(inv -> Optional.of(category(inv.getArgument(0), u)));
            given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of());

            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(
            null,10L, 7_000L, "식사", 0),
                new ExpenseSplitServiceDto.SplitCommand(
            null,11L, 3_000L, "음료", 1));

            org.assertj.core.api.Assertions.assertThatCode(() -> sut.replaceSplits(replaceCmd(splits)))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("분할 동기화 — 통째 교체가 행을 매번 새로 만들던 문제")
    class SplitSync {

        private Expense normalExpense() {
            User u = user(USER_ID);
            Expense expense = mock(Expense.class);
            given(expense.getUser()).willReturn(u);
            given(expense.getAmount()).willReturn(10_000L);
            lenient().when(expense.getExpenseType()).thenReturn(ExpenseType.EXPENSE);
            lenient().when(expense.isAutoGenerated()).thenReturn(false);
            given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
            given(expenseCategoryRepository.findById(anyLong()))
                .willAnswer(inv -> Optional.of(category(inv.getArgument(0), u)));
            return expense;
        }

        private ExpenseSplit existingSplit(long rowId, long amount) {
            ExpenseSplit sp = ExpenseSplit.create(
                mock(Expense.class), category(10L, user(USER_ID)), amount, "기존", 0);
            ReflectionTestUtils.setField(sp, "rowId", rowId);
            return sp;
        }

        @Test
        @DisplayName("rowId 를 보내면 그 행을 제자리에서 고친다 — 새로 만들지 않는다")
        void updatesInPlace() {
            normalExpense();
            ExpenseSplit kept = existingSplit(100L, 7_000L);
            given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of(kept));

            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(100L, 10L, 6_000L, "식사", 0),
                new ExpenseSplitServiceDto.SplitCommand(null, 11L, 4_000L, "음료", 1));
            sut.replaceSplits(replaceCmd(splits));

            // 기존 행은 살아서 값만 바뀐다
            assertThat(kept.getIsDeleted()).isEqualTo(YNType.N);
            assertThat(kept.getAmount()).isEqualTo(6_000L);
            // 새 것 하나만 저장된다 — 둘 다 새로 만들면 행이 매번 늘어난다
            verify(expenseSplitRepository, times(1)).save(any(ExpenseSplit.class));
        }

        @Test
        @DisplayName("목록에서 빠진 행은 지운다")
        void removesDropped() {
            normalExpense();
            ExpenseSplit dropped = existingSplit(101L, 3_000L);
            given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of(dropped));

            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(null, 10L, 10_000L, "식사", 0));
            sut.replaceSplits(replaceCmd(splits));

            assertThat(dropped.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("남의 분할 rowId 를 보내면 가져가지 못하고 신규가 된다")
        void foreignRowIdBecomesNew() {
            normalExpense();
            given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of());

            // 이 거래의 자식이 아닌 rowId — 맵에 없으니 못 찾는다
            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(999L, 10L, 10_000L, "식사", 0));
            sut.replaceSplits(replaceCmd(splits));

            verify(expenseSplitRepository, times(1)).save(any(ExpenseSplit.class));
        }

        @Test
        @DisplayName("시스템이 만든 거래에는 분할을 둘 수 없다 — 금액이 재계산으로 바뀐다")
        void rejectsAutoGenerated() {
            User u = user(USER_ID);
            Expense pl = mock(Expense.class);
            given(pl.getUser()).willReturn(u);
            given(pl.isAutoGenerated()).willReturn(true);
            given(expenseRepository.findById(5L)).willReturn(Optional.of(pl));

            var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(null, 10L, 10_000L, "손익", 0));

            assertThatThrownBy(() -> sut.replaceSplits(replaceCmd(splits)))
                .isInstanceOf(InvalidValueException.class);
            verify(expenseSplitRepository, never()).save(any(ExpenseSplit.class));
        }
    }
}
