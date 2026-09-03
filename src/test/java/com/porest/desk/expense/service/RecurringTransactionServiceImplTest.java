package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 반복 거래 정책 회귀 방지 단위 테스트 — 거래와 동일하게 leaf 카테고리만, 소유권 검증.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceImplTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private RecurringTransactionServiceImpl sut;

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

    private RecurringTransactionServiceDto.CreateCommand createCmd(long categoryRowId) {
        return new RecurringTransactionServiceDto.CreateCommand(
                USER_ID, categoryRowId, null, null, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private RecurringTransactionServiceDto.UpdateCommand updateCmd(long categoryRowId) {
        return new RecurringTransactionServiceDto.UpdateCommand(
                categoryRowId, null, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("createRecurring — 자식 보유(상위) 카테고리에는 반복 거래 불가")
    void createRejectsNonLeafCategory() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

        assertThatThrownBy(() -> sut.createRecurring(createCmd(10L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createRecurring — 남의 카테고리에는 반복 거래 불가")
    void createRejectsOthersCategory() {
        User u = user(USER_ID);
        ExpenseCategory othersCategory = category(20L, user(999L));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> sut.createRecurring(createCmd(20L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateRecurring — 자식 보유(상위) 카테고리로 변경 불가")
    void updateRejectsNonLeafCategory() {
        User u = user(USER_ID);
        RecurringTransaction recurring = mock(RecurringTransaction.class);
        given(recurring.getUser()).willReturn(u);
        ExpenseCategory parent = category(30L, u);
        given(recurringTransactionRepository.findById(5L)).willReturn(Optional.of(recurring));
        given(expenseCategoryRepository.findById(30L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(30L)).willReturn(true);

        assertThatThrownBy(() -> sut.updateRecurring(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("updateRecurring — 남의 반복 거래는 수정 불가")
    void updateRejectsOthersRecurring() {
        RecurringTransaction recurring = mock(RecurringTransaction.class);
        given(recurring.getUser()).willReturn(user(999L));
        given(recurringTransactionRepository.findById(5L)).willReturn(Optional.of(recurring));

        assertThatThrownBy(() -> sut.updateRecurring(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateRecurring — 남의 자산으로 변경 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersAsset() {
        User u = user(USER_ID);
        RecurringTransaction recurring = mock(RecurringTransaction.class);
        given(recurring.getUser()).willReturn(u);
        given(recurringTransactionRepository.findById(5L)).willReturn(Optional.of(recurring));
        Asset othersAsset = mock(Asset.class);
        given(othersAsset.getUser()).willReturn(user(999L));
        given(assetRepository.findById(20L)).willReturn(Optional.of(othersAsset));

        var cmd = new RecurringTransactionServiceDto.UpdateCommand(
                null, 20L, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> sut.updateRecurring(5L, USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getRecurrings — 음수 limit 은 거부(전체 반환 방지)")
    void getRecurringsRejectsNegativeLimit() {
        assertThatThrownBy(() -> sut.getRecurrings(USER_ID, false, -1))
                .isInstanceOf(InvalidValueException.class);
    }

    @Nested
    @DisplayName("금액 부호 — 실행될 때마다 잔액이 거꾸로 간다")
    class AmountSign {

        private RecurringTransactionServiceDto.CreateCommand cmdAmount(Long amount) {
            return new RecurringTransactionServiceDto.CreateCommand(
                USER_ID, 1L, null, null, ExpenseType.EXPENSE, amount,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("음수 금액으로 반복거래를 만들 수 없다 — 매달 잔액이 늘어난다")
        void rejectsNegative() {
            assertThatThrownBy(() -> sut.createRecurring(cmdAmount(-10_000L)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("0원 반복거래도 막는다")
        void rejectsZero() {
            assertThatThrownBy(() -> sut.createRecurring(cmdAmount(0L)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("수정에서도 음수를 막는다")
        void rejectsNegativeOnUpdate() {
            var cmd = new RecurringTransactionServiceDto.UpdateCommand(
                1L, null, ExpenseType.EXPENSE, -5_000L,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> sut.updateRecurring(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("100억 초과도 막는다 — 반복 설정이 거래 상한을 우회하는 경로였다(QA #54)")
        void rejectsOverTxLimit() {
            assertThatThrownBy(() -> sut.createRecurring(cmdAmount(10_000_000_001L)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("수정에서도 100억 초과를 막는다")
        void rejectsOverTxLimitOnUpdate() {
            var cmd = new RecurringTransactionServiceDto.UpdateCommand(
                1L, null, ExpenseType.EXPENSE, 10_000_000_001L,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> sut.updateRecurring(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class);
        }
    }
}
