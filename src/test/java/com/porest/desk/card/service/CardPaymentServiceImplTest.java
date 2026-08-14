package com.porest.desk.card.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 카드 결제 서비스 회귀 방지 단위 테스트 — 소유권 / 신용카드 검증 / 결제 자산 필수 +
 * 결제 회차(청구 기간 = 결제일의 전월 1일~말일) 금액·귀속 계산.
 */
@ExtendWith(MockitoExtension.class)
class CardPaymentServiceImplTest {

    @Mock private CardBillingRepository cardBillingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetService assetService;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private EntityManager entityManager;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private CardPaymentServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long CARD_ID = 5L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    /** 소유자 USER_ID 의 신용카드 mock — paymentDay·rowId 지정. */
    private Asset creditCard(Integer paymentDay) {
        Asset card = mock(Asset.class);
        lenient().when(card.getUser()).thenReturn(user(USER_ID));
        lenient().when(card.getAssetType()).thenReturn(AssetType.CREDIT_CARD);
        lenient().when(card.getRowId()).thenReturn(CARD_ID);
        lenient().when(card.getPaymentDay()).thenReturn(paymentDay);
        // 청구는 현재 빚을 넘지 않는다(캡). 회차 산수 자체를 보는 테스트가 캡에 걸리지 않도록
        // 빚을 넉넉히 잡아 둔다 — 캡·환급을 보는 테스트는 givenCardBalance 로 덮어쓴다.
        givenCardBalance(-100_000_000L);
        return card;
    }

    /** 카드 잔액 — 음수면 빚, 양수면 과납(환급 대상). */
    private void givenCardBalance(long balance) {
        lenient().when(balanceHistoryService.balanceAt(any(), any()))
            .thenReturn(new AssetBalanceHistoryService.Split(balance, 0L));
    }

    /**
     * cycleNetSpend JPQL mock — 일시불 순사용액(Long 쿼리) + 할부 회차 조회(Expense 쿼리).
     * 할부는 기본 없음으로 두고, 필요한 테스트만 {@link #givenInstallments} 로 덮어쓴다.
     */
    private void givenCycleSpend(long spend) {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        lenient().when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(spend);
        givenInstallments();
    }

    /** 할부 거래 목록 mock — 인자 없으면 할부 없음. */
    private void givenInstallments(Expense... installments) {
        @SuppressWarnings("unchecked")
        TypedQuery<Expense> query = mock(TypedQuery.class);
        lenient().when(entityManager.createQuery(anyString(), eq(Expense.class))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getResultList()).thenReturn(List.of(installments));
    }

    /** 카드 할부 거래 — 결제일(구매일)과 금액·개월로 만든다. */
    private Expense installment(LocalDate purchasedOn, long amount, int months) {
        return Expense.createExpense(
            null, null, null, ExpenseType.EXPENSE, amount, "할부 결제",
            purchasedOn.atTime(14, 0), "테스트가맹점", "CARD", months, null,
            null,
            null,
            null);
    }

    // === 회차(청구 기간) 계산 ===

    @Test
    @DisplayName("결제 회차 — 결제일(12) 경과 후에는 다음 결제일 회차(당월 1일~말일)로 넘어간다")
    void cycleRollsToCurrentMonthAfterPaymentDay() {
        // 7.24 기준 다음 결제일 = 8.12, 그 회차의 청구 기간 = 7.1 ~ 7.31
        LocalDate next = CardPaymentServiceImpl.nextPaymentDate(12, LocalDate.of(2026, 7, 24));
        assertThat(next).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(CardPaymentServiceImpl.periodStartFor(next)).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(CardPaymentServiceImpl.periodEndFor(next)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("결제 회차 — 결제일 당일 회차의 청구 기간은 전월 1일~말일")
    void cycleOnPaymentDayCoversPreviousMonth() {
        LocalDate next = CardPaymentServiceImpl.nextPaymentDate(12, LocalDate.of(2026, 7, 12));
        assertThat(next).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(CardPaymentServiceImpl.periodStartFor(next)).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(CardPaymentServiceImpl.periodEndFor(next)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    // === 할부 — 실제 카드 사용 시나리오 ===

    /**
     * 7/10 에 아이폰 150만원을 6개월 할부로 샀다. 카드 결제일은 12일.
     *
     * <p>일시불이면 8/12 에 150만원이 통째로 빠지지만, 할부라 25만원씩 6번에 나뉜다.
     * 8월 청구(7/1~7/31 사용분)는 1회차 25만원 + 그 달 일시불 사용분이어야 한다.
     */
    @Test
    @DisplayName("할부 — 7월에 산 150만원 6개월 할부는 8월 청구에 25만원만 잡힌다(일시불 3만원과 합산)")
    void installmentFirstCycle() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        // 오늘 7/24 → 다음 결제일 8/12 → 청구 기간 7/1~7/31
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(30_000L); // 7월 일시불 사용분(커피·편의점 등)
        givenInstallments(installment(LocalDate.of(2026, 7, 10), 1_500_000L, 6));
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        CardPaymentServiceDto.CardBillingInfo info = sut.getCardBilling(CARD_ID, USER_ID);

        assertThat(info.upcomingAmount()).isEqualTo(280_000L); // 30,000 + 250,000
    }

    @Test
    @DisplayName("할부 — 같은 거래가 다음 달 청구에도 25만원으로 이어진다(2회차)")
    void installmentSecondCycle() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        // 오늘 8/24 → 다음 결제일 9/12 → 청구 기간 8/1~8/31 (구매월 7월 기준 2회차)
        doReturn(LocalDate.of(2026, 8, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(0L); // 8월엔 일시불 사용 없음
        givenInstallments(installment(LocalDate.of(2026, 7, 10), 1_500_000L, 6));
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isEqualTo(250_000L);
    }

    @Test
    @DisplayName("할부 — 6개월이 끝난 뒤(7회차)에는 더 이상 청구되지 않는다")
    void installmentAfterLastCycle() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        // 오늘 2027-01-24 → 다음 결제일 2027-02-12 → 청구 기간 2027-01 (구매월 2026-07 기준 7회차)
        doReturn(LocalDate.of(2027, 1, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(0L);
        givenInstallments(installment(LocalDate.of(2026, 7, 10), 1_500_000L, 6));
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isZero();
    }

    @Test
    @DisplayName("할부 — 노트북 100만원 3개월(나머지 1원)은 첫 청구가 333,334원")
    void installmentUnevenFirstCycle() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(0L);
        givenInstallments(installment(LocalDate.of(2026, 7, 5), 1_000_000L, 3));
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isEqualTo(333_334L);
    }

    @Test
    @DisplayName("할부 — 여러 건이 겹치면 회차 금액이 합산된다(아이폰 2회차 + 냉장고 1회차)")
    void multipleInstallmentsOverlap() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        // 청구 기간 8/1~8/31 — 7월 구매분은 2회차, 8월 구매분은 1회차
        doReturn(LocalDate.of(2026, 8, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(15_000L);
        givenInstallments(
            installment(LocalDate.of(2026, 7, 10), 1_500_000L, 6),   // 2회차 250,000
            installment(LocalDate.of(2026, 8, 3), 2_400_000L, 12));  // 1회차 200,000
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isEqualTo(465_000L);
    }

    // === getCardBilling — 회차 금액 ===

    @Test
    @DisplayName("getCardBilling — 결제예정액 = 회차 순사용액 − 같은 회차 기결제액(선결제 차감)")
    void upcomingAmountIsCycleSpendMinusAlreadyPaid() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        givenCycleSpend(100_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(30_000L);

        CardPaymentServiceDto.CardBillingInfo info = sut.getCardBilling(CARD_ID, USER_ID);

        assertThat(info.upcomingAmount()).isEqualTo(70_000L);
        // 다가오는 회차의 청구 기간(전월 1일~말일)이 응답에 포함된다
        assertThat(info.upcomingPeriodStart()).isNotNull();
        assertThat(info.upcomingPeriodStart().getDayOfMonth()).isEqualTo(1);
        assertThat(info.upcomingPeriodEnd())
            .isEqualTo(info.upcomingPeriodStart().withDayOfMonth(info.upcomingPeriodStart().lengthOfMonth()));
    }

    @Test
    @DisplayName("getCardBilling — 기결제가 사용액 이상이면 결제예정액은 0 (음수 금지)")
    void upcomingAmountNeverNegative() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        givenCycleSpend(50_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(80_000L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isZero();
    }

    @Test
    @DisplayName("getCardBilling — 결제일 미설정 카드는 당월 1일~말일 사용액으로 청구한다")
    void upcomingUsesCalendarMonthWithoutPaymentDay() {
        Asset card = creditCard(null);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(33_800L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        CardPaymentServiceDto.CardBillingInfo info = sut.getCardBilling(CARD_ID, USER_ID);

        assertThat(info.upcomingAmount()).isEqualTo(33_800L);
        // 회차 기간이 잡힌다 — 종전엔 기간 없이 잔액 전액을 청구했다
        assertThat(info.upcomingPeriodStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(info.upcomingPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        // 결제일이 없는 건 사실이므로 그대로 null
        assertThat(info.nextPaymentDate()).isNull();
    }

    @Test
    @DisplayName("결제일 미설정 카드 — 잔액이 양수면 청구액 0(종전엔 절대값이라 양수도 또 청구했다)")
    void positiveBalanceWithoutPaymentDayBillsNothing() {
        Asset card = creditCard(null);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCardBalance(228_600L); // 과납 상태
        givenCycleSpend(50_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isZero();
    }

    // === 청구 캡 — 없는 빚을 청구하지 않는다 ===

    @Test
    @DisplayName("청구 캡 — 잔액을 수동 보정해 빚이 줄었으면 청구도 그만큼만(양수 역전 방지)")
    void billingIsCappedByCurrentDebt() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(500_000L);          // 기록상 사용액
        givenCardBalance(-120_000L);        // 실제 남은 빚(수동 보정으로 줄어듦)
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        // 500,000 을 그대로 청구하면 잔액이 +380,000 으로 뒤집힌다
        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("청구 캡 — 잔액이 이미 양수면 청구액 0")
    void positiveBalanceBillsNothing() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(500_000L);
        givenCardBalance(228_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isZero();
    }

    @Test
    @DisplayName("청구 캡 — 빚이 사용액보다 많으면 캡이 걸리지 않는다(할부 전액 선반영 등)")
    void capDoesNotShrinkNormalBilling() {
        Asset card = creditCard(12);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());
        doReturn(LocalDate.of(2026, 7, 24)).when(userClock).today(USER_ID);
        givenCycleSpend(280_000L);
        // 할부는 구매 시 전액이 잔액에 잡히므로 빚이 회차 청구보다 크다 — 정상 상태
        givenCardBalance(-1_530_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThat(sut.getCardBilling(CARD_ID, USER_ID).upcomingAmount()).isEqualTo(280_000L);
    }

    // === payCard — 선결제 귀속 ===

    @Test
    @DisplayName("payCard — 선결제는 다가오는 회차의 기간·금액으로 귀속된다(실행일의 전월 라벨 금지)")
    void manualPaymentBelongsToUpcomingCycle() {
        Asset paymentAsset = mock(Asset.class);
        lenient().when(paymentAsset.getRowId()).thenReturn(9L);
        Asset card = creditCard(12);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(70_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        AssetServiceDto.TransferInfo transfer = mock(AssetServiceDto.TransferInfo.class);
        given(transfer.rowId()).willReturn(77L);
        given(assetService.createTransfer(any())).willReturn(transfer);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        sut.payCard(CARD_ID, USER_ID, null);

        ArgumentCaptor<CardBilling> captor = ArgumentCaptor.forClass(CardBilling.class);
        verify(cardBillingRepository).save(captor.capture());
        CardBilling saved = captor.getValue();

        // 다가오는 결제일(nextPaymentDate)의 회차 = 그 결제일의 전월 1일~말일
        LocalDate next = CardPaymentServiceImpl.nextPaymentDate(12, LocalDate.now());
        assertThat(saved.getPeriodStart()).isEqualTo(CardPaymentServiceImpl.periodStartFor(next));
        assertThat(saved.getPeriodEnd()).isEqualTo(CardPaymentServiceImpl.periodEndFor(next));
        assertThat(saved.getBillingAmount()).isEqualTo(70_000L);
        assertThat(saved.getStatus()).isEqualTo(BillingStatus.COMPLETED);
    }

    @Test
    @DisplayName("payCard — 회차 잔여액 0이면 이체 없이 SKIPPED 기록")
    void manualPaymentSkipsWhenCycleSettled() {
        Asset paymentAsset = mock(Asset.class);
        Asset card = creditCard(12);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(50_000L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(50_000L);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID, null);

        assertThat(result.status()).isEqualTo(BillingStatus.SKIPPED);
        assertThat(result.billingAmount()).isZero();
    }

    // === 부분 선결제 · 기록용 앱 특례 ===

    @Test
    @DisplayName("부분 선결제 — 청구액 448,600 중 200,000만 결제하면 그 금액만 기록된다")
    void partialPrepayment() {
        Asset paymentAsset = mock(Asset.class);
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);
        AssetServiceDto.TransferInfo transfer = mock(AssetServiceDto.TransferInfo.class);
        given(transfer.rowId()).willReturn(77L);
        given(assetService.createTransfer(any())).willReturn(transfer);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID, 200_000L);

        assertThat(result.billingAmount()).isEqualTo(200_000L);
        assertThat(result.status()).isEqualTo(BillingStatus.COMPLETED);
    }

    @Test
    @DisplayName("부분 선결제 후 남은 청구액 — 448,600 중 200,000 냈으면 다음엔 248,600")
    void remainingAfterPartial() {
        Asset paymentAsset = mock(Asset.class);
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        // 청구액은 '사용액 − 이미 결제한 금액' 이라 선결제분이 자동으로 빠진다.
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(200_000L);
        AssetServiceDto.TransferInfo transfer = mock(AssetServiceDto.TransferInfo.class);
        given(transfer.rowId()).willReturn(78L);
        given(assetService.createTransfer(any())).willReturn(transfer);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID, null);

        assertThat(result.billingAmount()).isEqualTo(248_600L);
    }

    @Test
    @DisplayName("남은 청구액보다 많이 결제하려 하면 막는다")
    void rejectsOverpayment() {
        Asset paymentAsset = mock(Asset.class);
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThatThrownBy(() -> sut.payCard(CARD_ID, USER_ID, 500_000L))
            .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("0원 이하 결제는 막는다")
    void rejectsNonPositiveAmount() {
        Asset paymentAsset = mock(Asset.class);
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);

        assertThatThrownBy(() -> sut.payCard(CARD_ID, USER_ID, 0L))
            .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("결제계좌를 안 만들었어도 결제된다 — 이체 없이 카드 사용액만 정리")
    void paysWithoutPaymentAsset() {
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(null);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID, null);

        // 이체는 만들지 않고 카드에 상계 flow 만 쌓는다 — 등록 안 한 통장은 순자산에 없다.
        assertThat(result.status()).isEqualTo(BillingStatus.COMPLETED);
        assertThat(result.billingAmount()).isEqualTo(448_600L);
        then(assetService).should(never()).createTransfer(any());
        then(balanceHistoryService).should()
            .recordExpense(eq(card), isNull(), eq(ExpenseType.INCOME), eq(448_600L), any());
    }

    @Test
    @DisplayName("출금계좌 잔액이 모자라도 결제된다 — 기록용 앱이라 막지 않는다")
    void paysDespiteInsufficientBalance() {
        Asset paymentAsset = mock(Asset.class); // 통장 잔액을 안 맞춰 둔 상태
        Asset card = creditCard(25);
        given(card.getPaymentAsset()).willReturn(paymentAsset);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        givenCycleSpend(448_600L);
        given(cardBillingRepository.sumCompletedAmountByCardAndPeriod(eq(CARD_ID), any(), any()))
            .willReturn(0L);
        AssetServiceDto.TransferInfo transfer = mock(AssetServiceDto.TransferInfo.class);
        given(transfer.rowId()).willReturn(79L);
        given(assetService.createTransfer(any())).willReturn(transfer);
        given(cardBillingRepository.save(any(CardBilling.class)))
            .willAnswer(inv -> inv.getArgument(0));

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID, null);

        assertThat(result.status()).isEqualTo(BillingStatus.COMPLETED);
    }

    // === 검증 회귀 ===

    @Test
    @DisplayName("getCardBilling — 남의 카드는 조회 불가")
    void getBillingRejectsOthers() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(999L));
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.getCardBilling(CARD_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("payCard — 신용카드가 아니면 결제 불가")
    void payRejectsNonCreditCard() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(USER_ID));
        given(card.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.payCard(CARD_ID, USER_ID, null))
                .isInstanceOf(InvalidValueException.class);
    }


    // ── 결제 취소 ──────────────────────────────────────────────────

    private CardBilling completedBilling(AssetTransfer transfer) {
        // 카드 mock 을 먼저 만들어 둔다 — given(...) 인자 안에서 만들면 스터빙이 중첩된다.
        Asset card = creditCard(25);
        CardBilling b = mock(CardBilling.class);
        lenient().when(b.getIsDeleted()).thenReturn(YNType.N);
        lenient().when(b.getStatus()).thenReturn(BillingStatus.COMPLETED);
        lenient().when(b.getCardAsset()).thenReturn(card);
        lenient().when(b.getTransfer()).thenReturn(transfer);
        return b;
    }

    @Test
    @DisplayName("결제 취소 — 결제로 만든 이체를 무른다(잔액·청구가 그 연쇄로 되돌아간다)")
    void cancelPaymentRevertsTransfer() {
        AssetTransfer transfer = mock(AssetTransfer.class);
        given(transfer.getRowId()).willReturn(77L);
        // billing 도 먼저 만들어 둔다 — given(...) 인자 안에서 만들면 스터빙이 중첩된다.
        CardBilling billing = completedBilling(transfer);
        given(cardBillingRepository.findById(5L)).willReturn(Optional.of(billing));

        sut.cancelPayment(5L, USER_ID);

        verify(assetService).deleteTransfer(77L, USER_ID);
    }

    @Test
    @DisplayName("결제 취소 — 남의 카드는 못 되돌린다")
    void cancelPaymentRejectsOthers() {
        CardBilling b = mock(CardBilling.class);
        Asset othersCard = mock(Asset.class);
        given(othersCard.getUser()).willReturn(user(999L));
        given(b.getIsDeleted()).willReturn(YNType.N);
        given(b.getCardAsset()).willReturn(othersCard);
        given(cardBillingRepository.findById(5L)).willReturn(Optional.of(b));

        assertThatThrownBy(() -> sut.cancelPayment(5L, USER_ID))
            .isInstanceOf(InvalidValueException.class);
        verify(assetService, never()).deleteTransfer(anyLong(), anyLong());
    }

    @Test
    @DisplayName("결제 취소 — 이미 무른 회차는 다시 못 되돌린다")
    void cancelPaymentRejectsNonCompleted() {
        Asset card = creditCard(25);
        CardBilling b = mock(CardBilling.class);
        given(b.getIsDeleted()).willReturn(YNType.N);
        given(b.getStatus()).willReturn(BillingStatus.SKIPPED);
        given(b.getCardAsset()).willReturn(card);
        given(cardBillingRepository.findById(5L)).willReturn(Optional.of(b));

        assertThatThrownBy(() -> sut.cancelPayment(5L, USER_ID))
            .isInstanceOf(InvalidValueException.class);
        verify(assetService, never()).deleteTransfer(anyLong(), anyLong());
    }

    // === 과납 환급 스윕 — 결제일과 무관하게 매일 검사 ===

    /** 환급 스윕 대상 카드 1장으로 스케줄을 돌린다. */
    private Asset scheduleWithOneCard(Integer paymentDay, Asset paymentAsset, long balance) {
        Asset card = creditCard(paymentDay);
        lenient().when(card.getPaymentAsset()).thenReturn(paymentAsset);
        givenCardBalance(balance);
        given(assetRepository.findAllByType(AssetType.CREDIT_CARD)).willReturn(List.of(card));
        return card;
    }

    @Test
    @DisplayName("환급 — 잔액이 양수면 결제계좌로 돌려주는 이체를 만든다(카드→계좌, CARD_REFUND)")
    void refundsOverpaymentToPaymentAsset() {
        Asset paymentAsset = mock(Asset.class);
        lenient().when(paymentAsset.getRowId()).thenReturn(9L);
        // 결제일이 아닌 날 — 환급은 결제일과 무관하게 돌아야 한다
        scheduleWithOneCard(12, paymentAsset, 228_600L);

        sut.processDueCardPayments(LocalDate.of(2026, 7, 24));

        ArgumentCaptor<AssetServiceDto.CreateTransferCommand> captor =
            ArgumentCaptor.forClass(AssetServiceDto.CreateTransferCommand.class);
        verify(assetService).createTransfer(captor.capture());
        AssetServiceDto.CreateTransferCommand cmd = captor.getValue();
        // 방향이 결제와 반대다 — 카드에서 나가 결제계좌로 들어온다
        assertThat(cmd.fromAssetRowId()).isEqualTo(CARD_ID);
        assertThat(cmd.toAssetRowId()).isEqualTo(9L);
        assertThat(cmd.amount()).isEqualTo(228_600L);
        assertThat(cmd.autoSource()).isEqualTo("CARD_REFUND");
    }

    @Test
    @DisplayName("환급 — 결제계좌를 안 만들었으면 이체 없이 카드 잔액만 0으로 상계한다")
    void offsetsOverpaymentWithoutPaymentAsset() {
        scheduleWithOneCard(12, null, 228_600L);

        sut.processDueCardPayments(LocalDate.of(2026, 7, 24));

        verify(assetService, never()).createTransfer(any());
        // EXPENSE = 마이너스 flow — 양수 잔액을 0 으로 끌어내린다
        then(balanceHistoryService).should()
            .recordExpense(any(), isNull(), eq(ExpenseType.EXPENSE), eq(228_600L), any());
    }

    @Test
    @DisplayName("환급 — 잔액이 0이거나 빚이면 아무것도 만들지 않는다(멱등)")
    void doesNotRefundWhenNoSurplus() {
        Asset paymentAsset = mock(Asset.class);
        scheduleWithOneCard(12, paymentAsset, 0L);

        sut.processDueCardPayments(LocalDate.of(2026, 7, 24));

        verify(assetService, never()).createTransfer(any());
        then(balanceHistoryService).should(never())
            .recordExpense(any(), any(), eq(ExpenseType.EXPENSE), anyLong(), any());
    }

    @Test
    @DisplayName("환급 — 빚이 남은 정상 카드는 건드리지 않는다")
    void doesNotRefundWhenInDebt() {
        Asset paymentAsset = mock(Asset.class);
        scheduleWithOneCard(12, paymentAsset, -500_000L);

        sut.processDueCardPayments(LocalDate.of(2026, 7, 24));

        verify(assetService, never()).createTransfer(any());
    }

    @Test
    @DisplayName("환급은 CardBilling 을 만들지 않는다 — 청구 회차와 무관한 정산이다")
    void refundDoesNotCreateBilling() {
        Asset paymentAsset = mock(Asset.class);
        lenient().when(paymentAsset.getRowId()).thenReturn(9L);
        scheduleWithOneCard(12, paymentAsset, 100_000L);

        sut.processDueCardPayments(LocalDate.of(2026, 7, 24));

        verify(cardBillingRepository, never()).save(any(CardBilling.class));
    }
}
