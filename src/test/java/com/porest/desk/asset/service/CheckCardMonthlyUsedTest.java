package com.porest.desk.asset.service;

import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 체크카드의 이번 달 사용 합계(monthlyUsedAmount).
 *
 * <p>체크카드는 결제가 연결 계좌에서 즉시 빠져 잔액이 항상 0 이다 — 목록·상세가
 * "이 카드로 이번 달 얼마 썼는지" 를 보여줄 수 있게, 캘린더 월(1일~) 사용 합계를
 * 응답에 싣는다. 합산은 집계 규칙(예정 제외·환불 상계)을 그대로 따라야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckCardMonthlyUsedTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHoldingRepository assetHoldingRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private UserClock userClock;
    // 시장코드 확정은 mock 기본값(null) — 확정 못 한 경우와 같다.
    @Mock private com.porest.desk.stock.service.StockMasterResolver stockMasterResolver;
    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 12, 0);

    private User user;
    private Asset checkCard;
    private Asset bank;

    @BeforeEach
    void setUp() {
        user = User.createUser(1L, "tester", "테스터", "tester@porest.cloud");
        ReflectionTestUtils.setField(user, "rowId", USER_ID);
        given(userClock.now(USER_ID)).willReturn(NOW);

        checkCard = asset(20L, "국민체크카드", AssetType.CHECK_CARD);
        bank = asset(10L, "급여통장", AssetType.BANK_ACCOUNT);

        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(bank, checkCard));
        given(assetRepository.findById(any())).willAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id.equals(checkCard.getRowId())) return Optional.of(checkCard);
            if (id.equals(bank.getRowId())) return Optional.of(bank);
            return Optional.empty();
        });
        given(assetHoldingRepository.findActiveByAssets(any())).willReturn(List.of());
        given(assetHoldingRepository.findActiveByAsset(any())).willReturn(List.of());
        given(balanceHistoryService.balancesAt(any(), any())).willReturn(Map.of());
        given(balanceHistoryService.balanceAt(any(), any()))
            .willReturn(new AssetBalanceHistoryService.Split(0L, 0L));
    }

    private Asset asset(long rowId, String name, AssetType type) {
        Asset a = Asset.createAsset(user, name, type, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private Expense expense(Asset asset, ExpenseType type, long amount, LocalDateTime at) {
        Expense e = Expense.createExpense(user, null, asset, type, amount,
            "테스트", at, null, "CARD", null, null, null, null, null);
        return e;
    }

    @Test
    @DisplayName("체크카드 — 이번 달 사용 합계가 실린다 (환불 상계·예정 제외·타 자산 제외)")
    void monthlyUsedForCheckCard() {
        Expense spent1 = expense(checkCard, ExpenseType.EXPENSE, 30_000L, NOW.minusDays(3));
        Expense spent2 = expense(checkCard, ExpenseType.EXPENSE, 20_000L, NOW.minusDays(1));
        // 환불(INCOME + 원거래 지정)은 수입이 아니라 지출 상계다.
        Expense refund = Expense.createExpense(user, null, checkCard, ExpenseType.INCOME, 5_000L,
            "환불", NOW.minusDays(1), null, "CARD", null, 999L, null, null, null);
        // 아직 오지 않은 예정(반복거래 미리 생성분)은 안 센다.
        Expense future = expense(checkCard, ExpenseType.EXPENSE, 99_000L, NOW.plusDays(5));
        // 다른 자산(통장) 지출은 카드 사용액이 아니다.
        Expense other = expense(bank, ExpenseType.EXPENSE, 70_000L, NOW.minusDays(2));

        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of(spent1, spent2, refund, future, other));

        List<AssetServiceDto.AssetInfo> assets = sut.getAssets(USER_ID);

        AssetServiceDto.AssetInfo card = assets.stream()
            .filter(a -> a.assetType() == AssetType.CHECK_CARD).findFirst().orElseThrow();
        AssetServiceDto.AssetInfo account = assets.stream()
            .filter(a -> a.assetType() == AssetType.BANK_ACCOUNT).findFirst().orElseThrow();

        assertThat(card.monthlyUsedAmount()).isEqualTo(45_000L); // 30,000 + 20,000 − 5,000
        assertThat(account.monthlyUsedAmount()).isNull();        // 체크카드가 아니면 없다
    }

    @Test
    @DisplayName("체크카드 단건 조회에도 같은 값이 실린다")
    void monthlyUsedOnSingleGet() {
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of(expense(checkCard, ExpenseType.EXPENSE, 12_345L, NOW.minusDays(1))));

        AssetServiceDto.AssetInfo card = sut.getAsset(checkCard.getRowId(), USER_ID);

        assertThat(card.monthlyUsedAmount()).isEqualTo(12_345L);
    }

    @Test
    @DisplayName("사용 내역이 없는 체크카드는 null — 화면이 0원 사용으로 그리면 된다")
    void noExpensesMeansNull() {
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());

        List<AssetServiceDto.AssetInfo> assets = sut.getAssets(USER_ID);

        AssetServiceDto.AssetInfo card = assets.stream()
            .filter(a -> a.assetType() == AssetType.CHECK_CARD).findFirst().orElseThrow();
        assertThat(card.monthlyUsedAmount()).isNull();
    }
}
