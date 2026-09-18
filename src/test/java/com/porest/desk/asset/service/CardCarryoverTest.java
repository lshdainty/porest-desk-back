package com.porest.desk.asset.service;

import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.SecuritiesPriceProviders;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * 신용카드의 사용액은 **앵커가 아니라 거래 한 건**이다(D4, 2026-09-18 결정).
 *
 * <p>한도 사용(잔액)은 "마지막 앵커 + 그 뒤 흐름" 이고 청구는 "회차 거래 합" 이라 재료가
 * 다르다. 달 중간에 카드를 만들며 사용액을 앵커로 찍어 두면, 나중에 적은 <b>지난 날짜</b>
 * 지출이 청구에는 들어가고 잔액에는 안 잡혀 두 숫자가 어긋난다(운영 ZERO 카드 사고와 같은
 * 기전). 사용액을 거래로 두면 둘이 같은 재료에서 나온다.
 *
 * <p>여기서 잠그는 것 넷.
 *   1) 카드를 사용액과 함께 만들면 그 금액짜리 <b>시스템 지출</b> 한 건이 생긴다
 *   2) 카드 편집의 그 칸은 <b>같은 거래의 금액</b>을 고친다 — 앵커(MANUAL)를 찍지 않는다
 *   3) 0 으로 고치면 그 거래를 지운다
 *   4) 통장·대출은 종전대로 앵커를 쓴다 — 카드 규칙이 새지 않게
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("신용카드 이전 미결제 사용액(D4)")
class CardCarryoverTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHoldingRepository assetHoldingRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private com.porest.desk.card.repository.CardBillingRepository cardBillingRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private com.porest.desk.subscription.service.SubscriptionEntitlementService entitlementService;
    @Mock private com.porest.desk.securities.service.SecuritiesCredentialService securitiesCredentialService;
    @Mock private SecuritiesPriceProviders priceProviders;
    @Mock private SecuritiesPriceProvider priceProvider;
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));
    @Mock private com.porest.desk.stock.service.StockMasterResolver stockMasterResolver;

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long CARD_ID = 9L;

    @org.junit.jupiter.api.BeforeEach
    void stubBalances() {
        lenient().when(balanceHistoryService.balanceAt(any(), any()))
            .thenReturn(AssetBalanceHistoryService.Split.ZERO);
        lenient().when(balanceHistoryService.balancesAt(anyCollection(), any()))
            .thenReturn(java.util.Map.of());
        lenient().when(expenseRepository.findActiveByAssetAndAutoSource(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        lenient().when(expenseRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset card(long rowId) {
        Asset a = Asset.createAsset(user(USER_ID), "현대카드", AssetType.CREDIT_CARD, -100_000L,
            "KRW", null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private AssetServiceDto.CreateAssetCommand createCommand(AssetType type, Long balance) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "현대카드", type, balance, null, "KRW",
            null, null, null, null, 0,
            YNType.Y, YNType.N, null, null, null, null, List.of());
    }

    private AssetServiceDto.UpdateAssetCommand balanceTo(Long balance) {
        return new AssetServiceDto.UpdateAssetCommand(
            Patch.absent(), Patch.absent(), Patch.set(balance), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null);
    }

    private Expense savedCarryover() {
        ArgumentCaptor<Expense> c = ArgumentCaptor.forClass(Expense.class);
        then(expenseRepository).should().save(c.capture());
        return c.getValue();
    }

    @Nested
    @DisplayName("카드 생성")
    class OnCreate {

        @Test
        @DisplayName("사용액을 적으면 그 금액짜리 시스템 지출이 생긴다 — 첫 회차에 청구된다")
        void usageBecomesExpense() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            sut.createAsset(createCommand(AssetType.CREDIT_CARD, 100_000L));

            Expense carryover = savedCarryover();
            assertThat(carryover.getAmount()).isEqualTo(100_000L);
            assertThat(carryover.getExpenseType()).isEqualTo(ExpenseType.EXPENSE);
            assertThat(carryover.getAutoSource())
                .as("가계부에서 직접 못 고친다 — 고치는 자리는 카드 편집 폼이다")
                .isEqualTo("CARD_CARRYOVER");
            assertThat(carryover.getCategory())
                .as("사용자 분류가 아니다")
                .isNull();
            // 흐름까지 깔려야 잔액이 움직인다.
            then(balanceHistoryService).should()
                .recordExpense(any(), any(), eq(ExpenseType.EXPENSE), eq(100_000L), any());
        }

        @Test
        @DisplayName("사용액 0 이면 거래를 만들지 않는다")
        void zeroUsageMakesNothing() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            sut.createAsset(createCommand(AssetType.CREDIT_CARD, 0L));

            then(expenseRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("통장은 대상이 아니다 — 초기 잔액은 앵커가 든다")
        void bankAccountUntouched() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            sut.createAsset(createCommand(AssetType.BANK_ACCOUNT, 500_000L));

            then(expenseRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("카드 편집 — 그 칸은 이월 거래를 고친다")
    class OnUpdate {

        @Test
        @DisplayName("금액을 바꾸면 거래 금액이 바뀌고 앵커는 안 찍는다")
        void editChangesExpenseNotAnchor() {
            Asset asset = card(CARD_ID);
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense existing = Expense.createExpense(
                user(USER_ID), null, asset, ExpenseType.EXPENSE, 100_000L, "이전 미결제 사용액",
                java.time.LocalDateTime.of(2026, 9, 1, 10, 0), "이전 미결제 사용액",
                null, null, null, null, null);
            ReflectionTestUtils.setField(existing, "rowId", 7L);
            given(expenseRepository.findActiveByAssetAndAutoSource(CARD_ID, "CARD_CARRYOVER"))
                .willReturn(Optional.of(existing));

            sut.updateAsset(CARD_ID, USER_ID, balanceTo(40_000L));

            assertThat(existing.getAmount()).isEqualTo(40_000L);
            then(balanceHistoryService).should(never()).recordManual(any(), anyLong(), any());
            // 흐름은 지웠다 다시 깐다 — 금액만 고치는 경로가 따로 없다.
            then(balanceHistoryService).should().removeExpense(7L);
            then(balanceHistoryService).should()
                .recordExpense(any(), eq(7L), eq(ExpenseType.EXPENSE), eq(40_000L), any());
        }

        @Test
        @DisplayName("0 으로 고치면 그 거래를 지운다 — 사용액이 없다는 뜻이다")
        void zeroDeletesExpense() {
            Asset asset = card(CARD_ID);
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense existing = Expense.createExpense(
                user(USER_ID), null, asset, ExpenseType.EXPENSE, 100_000L, "이전 미결제 사용액",
                java.time.LocalDateTime.of(2026, 9, 1, 10, 0), "이전 미결제 사용액",
                null, null, null, null, null);
            ReflectionTestUtils.setField(existing, "rowId", 7L);
            given(expenseRepository.findActiveByAssetAndAutoSource(CARD_ID, "CARD_CARRYOVER"))
                .willReturn(Optional.of(existing));

            sut.updateAsset(CARD_ID, USER_ID, balanceTo(0L));

            assertThat(existing.getIsDeleted()).isEqualTo(YNType.Y);
            then(balanceHistoryService).should().removeExpense(7L);
            then(balanceHistoryService).should(never()).recordManual(any(), anyLong(), any());
        }
    }
}
