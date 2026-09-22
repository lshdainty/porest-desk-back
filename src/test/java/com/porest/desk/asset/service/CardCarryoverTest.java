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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
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
    // 회차별 결제일(D5) — 이력이 비어 있으면 카드의 지금 결제일 하나로 센다. 닫힌 회차의 "오늘"은 서울 시계(D11).
    private final com.porest.desk.asset.repository.AssetPaymentDayHistoryRepository paymentDayHistory =
        org.mockito.Mockito.mock(com.porest.desk.asset.repository.AssetPaymentDayHistoryRepository.class);
    @org.mockito.Spy private com.porest.desk.card.service.PaymentScheduleService paymentScheduleService =
        new com.porest.desk.card.service.PaymentScheduleService(paymentDayHistory);
    @org.mockito.Spy private com.porest.core.time.ServiceClock serviceClock =
        new com.porest.core.time.ServiceClock("Asia/Seoul");

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
        // 이월 거래(9/1)가 든 9월 회차는 10/12 결제 — 그 전으로 고정해 이월 칸이 열려 있게 한다(D15).
        lenient().doReturn(java.time.LocalDate.of(2026, 9, 10)).when(serviceClock).today();
    }

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset card(long rowId) {
        Asset a = Asset.createAsset(user(USER_ID), "현대카드", AssetType.CREDIT_CARD, -100_000L,
            "KRW", null, null, null, null, 0, YNType.Y, null, null, 12, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private AssetServiceDto.CreateAssetCommand createCommand(AssetType type, Long balance) {
        // 신용카드는 결제일이 있어야 한다(D8).
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "현대카드", type, balance, null, "KRW",
            null, null, null, null, 0,
            YNType.Y, YNType.N, null, null, type == AssetType.CREDIT_CARD ? 12 : null, null, List.of());
    }

    /** 옛 앱처럼 잔액 칸에 값을 실어 보낸다 — 신용카드는 무시한다(D7). */
    private AssetServiceDto.UpdateAssetCommand balanceTo(Long balance) {
        return new AssetServiceDto.UpdateAssetCommand(
            Patch.absent(), Patch.absent(), Patch.set(balance), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null);
    }

    /** 이월 금액 칸(D7) — 신용카드는 이 키로만 이월 거래를 고친다. */
    private AssetServiceDto.UpdateAssetCommand carryoverTo(Long amount) {
        return new AssetServiceDto.UpdateAssetCommand(
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null,
            Patch.set(amount));
    }

    private Expense existingCarryover(Asset asset) {
        Expense existing = Expense.createExpense(
            user(USER_ID), null, asset, ExpenseType.EXPENSE, 100_000L, "이전 미결제 사용액",
            java.time.LocalDateTime.of(2026, 9, 1, 10, 0), "이전 미결제 사용액",
            null, null, null, null, null);
        ReflectionTestUtils.setField(existing, "rowId", 7L);
        given(expenseRepository.findActiveByAssetAndAutoSource(CARD_ID, "CARD_CARRYOVER"))
            .willReturn(Optional.of(existing));
        return existing;
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
        @DisplayName("잔액 칸은 무시한다 — 옛 폼이 지금 잔액을 실어 보내 이월 거래를 덮어쓰던 결함(D7)")
        void balanceIsIgnored() {
            Asset asset = card(CARD_ID);
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense existing = existingCarryover(asset);

            var info = sut.updateAsset(CARD_ID, USER_ID, balanceTo(-999_000L));

            assertThat(existing.getAmount()).isEqualTo(100_000L);
            assertThat(info.carryoverAmount()).isEqualTo(100_000L);
            then(balanceHistoryService).should(never()).removeExpense(7L);
        }

        @Test
        @DisplayName("이월 거래가 든 회차의 결제일이 되면 금액을 못 고친다(D15)")
        void lockedAfterPaymentDay() {
            Asset asset = card(CARD_ID);
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense existing = existingCarryover(asset);
            doReturn(java.time.LocalDate.of(2026, 10, 12)).when(serviceClock).today();

            assertThatThrownBy(() -> sut.updateAsset(CARD_ID, USER_ID, carryoverTo(40_000L)))
                .isInstanceOf(com.porest.core.exception.InvalidValueException.class)
                .hasMessage(com.porest.desk.common.exception.DeskErrorCode.ASSET_CARD_CARRYOVER_LOCKED.getMessageKey());
            assertThat(existing.getAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("금액을 바꾸면 거래 금액이 바뀌고 앵커는 안 찍는다")
        void editChangesExpenseNotAnchor() {
            Asset asset = card(CARD_ID);
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense existing = existingCarryover(asset);

            sut.updateAsset(CARD_ID, USER_ID, carryoverTo(40_000L));

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
            Expense existing = existingCarryover(asset);

            sut.updateAsset(CARD_ID, USER_ID, carryoverTo(0L));

            assertThat(existing.getIsDeleted()).isEqualTo(YNType.Y);
            then(balanceHistoryService).should().removeExpense(7L);
            then(balanceHistoryService).should(never()).recordManual(any(), anyLong(), any());
        }
    }

    // ─── 결제 대기 청구분(2026-09-22 사용자 결정) ────────────────────────────
    //
    // 결제일 전에 카드를 등록하면 실제 카드사는 지난달 청구분을 다가오는 결제일에, 이번 달 쓴
    // 금액을 그다음 결제일에 뺀다. 한 건으로 두면 등록한 날의 회차에 한꺼번에 청구돼 한 달 동안
    // 통장 잔액이 실제보다 많았다. 그래서 청구분은 지난달 회차(말일) 거래로 따로 둔다.
    //
    // 이 카드(결제일 12)를 9/10 에 등록하면 8월 회차가 9/12 결제를 기다린다.

    private static final java.time.LocalDate REG_DAY = java.time.LocalDate.of(2026, 9, 10);

    private AssetServiceDto.CreateAssetCommand createCardWithDue(Long current, Long due) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "현대카드", AssetType.CREDIT_CARD, current, null, "KRW",
            null, null, null, null, 0,
            YNType.Y, YNType.N, null, null, 12, null, List.of(), due);
    }

    private AssetServiceDto.UpdateAssetCommand dueTo(Long amount) {
        return new AssetServiceDto.UpdateAssetCommand(
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null,
            Patch.absent(), Patch.set(amount));
    }

    /** 등록 시각(UTC 저장) — 9/10 10:00 KST = 9/10 01:00 UTC. */
    private Asset registeredCard(java.time.LocalDateTime createAtUtc) {
        Asset a = card(CARD_ID);
        ReflectionTestUtils.setField(a, "createAt", createAtUtc);
        return a;
    }

    private Expense existingDue(Asset asset, long amount) {
        Expense e = Expense.createExpense(
            user(USER_ID), null, asset, ExpenseType.EXPENSE, amount, "이전 미결제 사용액",
            java.time.LocalDateTime.of(2026, 8, 31, 0, 0), "이전 미결제 사용액",
            null, null, null, null, null);
        e.markAutoGenerated(Expense.AUTO_SOURCE_CARD_CARRYOVER_DUE);
        ReflectionTestUtils.setField(e, "rowId", 8L);
        given(expenseRepository.findActiveByAssetAndAutoSource(CARD_ID, "CARD_CARRYOVER_DUE"))
            .willReturn(Optional.of(e));
        return e;
    }

    /** 서비스·사용자 달력 둘 다 그날로 — 생성은 사용자 달력, 잠금 판정은 서비스 달력을 본다. */
    private void today(java.time.LocalDate d) {
        lenient().doReturn(d).when(serviceClock).today();
        lenient().doReturn(d).when(userClock).today(USER_ID);
    }

    @Nested
    @DisplayName("결제 대기 청구분 — 다가오는 결제일에 따로 청구된다")
    class DueCarryover {

        @Test
        @DisplayName("결제일 전 등록 — 청구분은 지난달 말 거래(CARD_CARRYOVER_DUE), 나머지는 등록 시각 거래")
        void splitOnCreate() {
            today(REG_DAY);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            sut.createAsset(createCardWithDue(10_000L, 30_000L));

            ArgumentCaptor<Expense> c = ArgumentCaptor.forClass(Expense.class);
            then(expenseRepository).should(org.mockito.Mockito.times(2)).save(c.capture());
            Expense current = c.getAllValues().get(0);
            Expense due = c.getAllValues().get(1);
            assertThat(current.getAutoSource()).isEqualTo("CARD_CARRYOVER");
            assertThat(current.getAmount()).isEqualTo(10_000L);
            assertThat(due.getAutoSource()).isEqualTo("CARD_CARRYOVER_DUE");
            assertThat(due.getAmount()).isEqualTo(30_000L);
            assertThat(due.getExpenseDate())
                .as("8월 회차(9/12 결제)에 들어가야 다가오는 결제일에 청구된다")
                .isEqualTo(java.time.LocalDateTime.of(2026, 8, 31, 0, 0));
            assertThat(due.isCardCarryover()).as("가계부에서 빠지고 못 고친다").isTrue();
            then(balanceHistoryService).should().recordExpense(any(), any(), eq(ExpenseType.EXPENSE),
                eq(30_000L), eq(java.time.LocalDateTime.of(2026, 8, 31, 0, 0)));
        }

        @Test
        @DisplayName("청구분 0 이면 그 거래를 안 만든다")
        void zeroDueMakesNothing() {
            today(REG_DAY);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            sut.createAsset(createCardWithDue(10_000L, 0L));

            ArgumentCaptor<Expense> c = ArgumentCaptor.forClass(Expense.class);
            then(expenseRepository).should().save(c.capture());
            assertThat(c.getValue().getAutoSource()).isEqualTo("CARD_CARRYOVER");
        }

        @Test
        @DisplayName("결제일 당일·그 뒤 등록 — 기다리는 청구분이 없어 AST_035")
        void noPendingBillOnOrAfterPaymentDay() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            for (java.time.LocalDate d : List.of(
                    java.time.LocalDate.of(2026, 9, 12), java.time.LocalDate.of(2026, 9, 20))) {
                today(d);
                assertThatThrownBy(() -> sut.createAsset(createCardWithDue(10_000L, 30_000L)))
                    .as(d.toString())
                    .isInstanceOf(com.porest.core.exception.InvalidValueException.class)
                    .hasMessage(com.porest.desk.common.exception.DeskErrorCode.ASSET_CARD_DUE_CARRYOVER_CLOSED.getMessageKey());
            }
        }

        @Test
        @DisplayName("결제일 전 수정 — 금액이 바뀐다")
        void editBeforePaymentDay() {
            today(REG_DAY);
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense due = existingDue(asset, 30_000L);

            sut.updateAsset(CARD_ID, USER_ID, dueTo(25_000L));

            assertThat(due.getAmount()).isEqualTo(25_000L);
            then(balanceHistoryService).should().removeExpense(8L);
            then(balanceHistoryService).should()
                .recordExpense(any(), eq(8L), eq(ExpenseType.EXPENSE), eq(25_000L), any());
        }

        @Test
        @DisplayName("결제일이 되면 잠긴다 — 이미 결제에 들어갔다(AST_034)")
        void lockedOnPaymentDay() {
            today(java.time.LocalDate.of(2026, 9, 12));
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense due = existingDue(asset, 30_000L);

            assertThatThrownBy(() -> sut.updateAsset(CARD_ID, USER_ID, dueTo(25_000L)))
                .isInstanceOf(com.porest.core.exception.InvalidValueException.class)
                .hasMessage(com.porest.desk.common.exception.DeskErrorCode.ASSET_CARD_CARRYOVER_LOCKED.getMessageKey());
            assertThat(due.getAmount()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("0 으로 고치면 지운다")
        void zeroDeletes() {
            today(REG_DAY);
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            Expense due = existingDue(asset, 30_000L);

            sut.updateAsset(CARD_ID, USER_ID, dueTo(0L));

            assertThat(due.getIsDeleted()).isEqualTo(YNType.Y);
            then(balanceHistoryService).should().removeExpense(8L);
        }

        @Test
        @DisplayName("등록한 달의 그 회차가 열려 있으면 수정에서 새로 넣을 수 있다")
        void addOnEditWhileOpen() {
            today(java.time.LocalDate.of(2026, 9, 11));
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));

            sut.updateAsset(CARD_ID, USER_ID, dueTo(20_000L));

            Expense saved = savedCarryover();
            assertThat(saved.getAutoSource()).isEqualTo("CARD_CARRYOVER_DUE");
            assertThat(saved.getExpenseDate()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 31, 0, 0));
        }

        @Test
        @DisplayName("등록한 달이 아니면(지난달에 등록한 카드) 새로 못 넣는다 — 그 회차 거래는 이미 앱에 있다")
        void noAddForOlderCard() {
            today(REG_DAY);
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 8, 5, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));

            assertThatThrownBy(() -> sut.updateAsset(CARD_ID, USER_ID, dueTo(20_000L)))
                .isInstanceOf(com.porest.core.exception.InvalidValueException.class)
                .hasMessage(com.porest.desk.common.exception.DeskErrorCode.ASSET_CARD_DUE_CARRYOVER_CLOSED.getMessageKey());
            then(expenseRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("응답 칸 — 열린 동안은 (금액, 안 잠김, 9/12), 결제일이 지나면 잠김, 없고 지났으면 칸 없음")
        void responseState() {
            Asset asset = registeredCard(java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
            given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(asset));
            java.time.LocalDate pay = java.time.LocalDate.of(2026, 9, 12);

            today(REG_DAY);
            assertThat(sut.getAsset(CARD_ID, USER_ID).dueCarryover())
                .as("없어도 열려 있으면 칸을 그린다(0)")
                .isEqualTo(new AssetServiceDto.DueCarryover(0L, false, pay));

            existingDue(asset, 30_000L);
            assertThat(sut.getAsset(CARD_ID, USER_ID).dueCarryover())
                .isEqualTo(new AssetServiceDto.DueCarryover(30_000L, false, pay));

            today(pay);
            assertThat(sut.getAsset(CARD_ID, USER_ID).dueCarryover())
                .as("결제일이 되면 읽기 전용")
                .isEqualTo(new AssetServiceDto.DueCarryover(30_000L, true, pay));

            given(expenseRepository.findActiveByAssetAndAutoSource(CARD_ID, "CARD_CARRYOVER_DUE"))
                .willReturn(Optional.empty());
            assertThat(sut.getAsset(CARD_ID, USER_ID).dueCarryover())
                .as("없고 결제일이 지났으면 칸이 없다")
                .isNull();
        }
    }
}
