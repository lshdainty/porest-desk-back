package com.porest.desk.card.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.porest.desk.common.time.ServiceClock;
import com.porest.desk.common.time.UserClock;

/**
 * 카드 결제 서비스 회귀 방지 단위 테스트 — 소유권 / 신용카드 검증 / 결제 자산 필수 +
 * 결제 회차(청구 기간 = 결제일의 전월 1일~말일) 금액·귀속 계산.
 */
@ExtendWith(MockitoExtension.class)
class CardPaymentServiceImplTest {

    @Mock private CardBillingRepository cardBillingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetService assetService;
    @Mock private EntityManager entityManager;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(
            org.mockito.Mockito.mock(com.porest.desk.user.repository.UserRepository.class),
            new ServiceClock("Asia/Seoul"));

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
        return card;
    }

    /** cycleNetSpend JPQL mock — 회차 순사용액 반환. */
    private void givenCycleSpend(long spend) {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        lenient().when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(spend);
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
    @DisplayName("getCardBilling — 결제일 미설정 카드는 잔액 전액 fallback(기간 null)")
    void upcomingFallsBackToBalanceWithoutPaymentDay() {
        Asset card = creditCard(null);
        given(card.getBalance()).willReturn(-33_800L);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));
        given(cardBillingRepository.findByCardAssetRowId(CARD_ID)).willReturn(List.of());

        CardPaymentServiceDto.CardBillingInfo info = sut.getCardBilling(CARD_ID, USER_ID);

        assertThat(info.upcomingAmount()).isEqualTo(33_800L);
        assertThat(info.upcomingPeriodStart()).isNull();
        assertThat(info.nextPaymentDate()).isNull();
    }

    // === payCard — 선결제 귀속 ===

    @Test
    @DisplayName("payCard — 선결제는 다가오는 회차의 기간·금액으로 귀속된다(실행일의 전월 라벨 금지)")
    void manualPaymentBelongsToUpcomingCycle() {
        Asset paymentAsset = mock(Asset.class);
        lenient().when(paymentAsset.getRowId()).thenReturn(9L);
        lenient().when(paymentAsset.getBalance()).thenReturn(1_000_000L);
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

        sut.payCard(CARD_ID, USER_ID);

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

        CardPaymentServiceDto.BillingInfo result = sut.payCard(CARD_ID, USER_ID);

        assertThat(result.status()).isEqualTo(BillingStatus.SKIPPED);
        assertThat(result.billingAmount()).isZero();
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

        assertThatThrownBy(() -> sut.payCard(CARD_ID, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("payCard — 결제 자산이 없으면 결제 불가")
    void payRejectsWhenNoPaymentAsset() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(USER_ID));
        given(card.getAssetType()).willReturn(AssetType.CREDIT_CARD);
        given(card.getPaymentAsset()).willReturn(null);
        given(assetRepository.findById(CARD_ID)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.payCard(CARD_ID, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }
}
