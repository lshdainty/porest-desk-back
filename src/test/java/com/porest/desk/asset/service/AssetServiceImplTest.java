package com.porest.desk.asset.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import com.porest.desk.securities.service.SecuritiesCredentialService;
import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.SecuritiesPriceProviders;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 자산 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 자산/이체는 조회·수정·삭제·이체할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock private AssetRepository assetRepository;
    @Mock private com.porest.desk.asset.repository.AssetHoldingRepository assetHoldingRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private com.porest.desk.card.repository.CardBillingRepository cardBillingRepository;
    // 이체 삭제 시 이자로 만든 지출 거래를 함께 무르므로 필요 — mock 이 없으면 NPE.
    @Mock private com.porest.desk.expense.repository.ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private SubscriptionEntitlementService entitlementService;
    @Mock private SecuritiesCredentialService securitiesCredentialService;
    @Mock private SecuritiesPriceProviders priceProviders;
    @Mock private SecuritiesPriceProvider priceProvider;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;


    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset assetOwnedBy(long ownerRowId) {
        Asset a = mock(Asset.class);
        // 자동 생성 이체처럼 소유권 검사 전에 막히는 경로도 있어 lenient 로 둔다.
        lenient().when(a.getUser()).thenReturn(user(ownerRowId));
        return a;
    }

    @Test
    @DisplayName("getAsset — 남의 자산은 조회 불가")
    void getRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.getAsset(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateAsset — 남의 자산은 수정 불가")
    void updateRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.updateAsset(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteAsset — 남의 자산은 삭제 불가")
    void deleteRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.deleteAsset(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 출금 자산이 남의 것이면 이체 불가")
    void transferRejectsOthersFromAsset() {
        Asset fromAsset = assetOwnedBy(999L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 입금 자산이 남의 것이면 이체 불가")
    void transferRejectsOthersToAsset() {
        Asset fromAsset = assetOwnedBy(USER_ID);
        Asset toAsset = assetOwnedBy(999L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));
        given(assetRepository.findById(11L)).willReturn(Optional.of(toAsset));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 같은 자산으로의 이체는 불가")
    void transferRejectsSameAsset() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 10L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTransfer — 이체 금액이 0 이하면 불가(음수 자금 역류 방지)")
    void transferRejectsNonPositiveAmount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, -50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTransfer — 성공 시 잔액 이력(recordTransfer)을 기록한다")
    void transferRecordsBalanceHistory() {
        Asset fromAsset = assetOwnedBy(USER_ID);
        Asset toAsset = assetOwnedBy(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));
        given(assetRepository.findById(11L)).willReturn(Optional.of(toAsset));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 30_000L, 1_000L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);
        sut.createTransfer(cmd);

        verify(balanceHistoryService).recordTransfer(any(AssetTransfer.class));
    }

    @Test
    @DisplayName("createTransfer — 체크카드는 이체 대상이 될 수 없다(잔액을 들지 않는 자산)")
    void transferRejectsCheckCard() {
        Asset fromAsset = assetOwnedBy(USER_ID);
        Asset checkCard = assetOwnedBy(USER_ID);
        given(checkCard.getAssetType()).willReturn(AssetType.CHECK_CARD);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));
        given(assetRepository.findById(11L)).willReturn(Optional.of(checkCard));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 30_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
        verify(balanceHistoryService, never()).recordTransfer(any(AssetTransfer.class));
    }

    @Test
    @DisplayName("createTransfer — 신용카드는 결제일 자동이체 대상이라 그대로 허용한다")
    void transferAllowsCreditCard() {
        Asset fromAsset = assetOwnedBy(USER_ID);
        Asset creditCard = assetOwnedBy(USER_ID);
        given(creditCard.getAssetType()).willReturn(AssetType.CREDIT_CARD);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));
        given(assetRepository.findById(11L)).willReturn(Optional.of(creditCard));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 30_000L, 0L, 0L, "카드 결제", LocalDate.of(2026, 6, 1).atStartOfDay(), null);
        sut.createTransfer(cmd);

        verify(balanceHistoryService).recordTransfer(any(AssetTransfer.class));
    }

    @Test
    @DisplayName("reorderAssets — 남의 자산 순서는 변경 불가(소유권 검증 누락 보강)")
    void reorderRejectsOthers() {
        Asset others = assetOwnedBy(999L);
        given(assetRepository.findById(10L)).willReturn(Optional.of(others));

        var items = List.of(new AssetServiceDto.ReorderItem(10L, 1));

        assertThatThrownBy(() -> sut.reorderAssets(USER_ID, items))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteTransfer — 카드 결제 이체를 지우면 그 청구 회차도 함께 취소된다")
    void deleteTransferCancelsLinkedCardBilling() {
        AssetTransfer payment = mock(AssetTransfer.class);
        given(payment.getUser()).willReturn(user(USER_ID));
        given(assetTransferRepository.findById(900L)).willReturn(Optional.of(payment));
        CardBilling billing = mock(CardBilling.class);
        given(cardBillingRepository.findActiveByTransfer(900L)).willReturn(Optional.of(billing));

        sut.deleteTransfer(900L, USER_ID);

        // 청구가 COMPLETED 로 남으면 '이미 냈다'로 집계돼 다음 청구액이 0 이 되고,
        // 카드 부채가 영원히 안 갚아진다.
        verify(billing).cancel();
        verify(balanceHistoryService).removeTransfer(900L);
    }

    @Test
    @DisplayName("deleteTransfer — 일반 이체는 청구와 무관하므로 그냥 지운다")
    void deleteOrdinaryTransferTouchesNoBilling() {
        AssetTransfer plain = mock(AssetTransfer.class);
        given(plain.getUser()).willReturn(user(USER_ID));
        given(assetTransferRepository.findById(901L)).willReturn(Optional.of(plain));
        given(cardBillingRepository.findActiveByTransfer(901L)).willReturn(Optional.empty());

        sut.deleteTransfer(901L, USER_ID);

        verify(balanceHistoryService).removeTransfer(901L);
    }

    @Test
    @DisplayName("deleteTransfer — 남의 이체는 삭제 불가")
    void deleteTransferRejectsOthers() {
        AssetTransfer transfer = mock(AssetTransfer.class);
        given(transfer.getUser()).willReturn(user(999L));
        given(assetTransferRepository.findById(7L)).willReturn(Optional.of(transfer));

        assertThatThrownBy(() -> sut.deleteTransfer(7L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getNetWorthTrend — 음수 months 는 거부")
    void netWorthTrendRejectsNegativeMonths() {
        assertThatThrownBy(() -> sut.getNetWorthTrend(USER_ID, -1))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("getAssetBalanceTrend — 음수 weeks 는 거부")
    void balanceTrendRejectsNegativeWeeks() {
        assertThatThrownBy(() -> sut.getAssetBalanceTrend(5L, USER_ID, -1))
                .isInstanceOf(InvalidValueException.class);
    }

    // === 토스 연결 게이트 ===

    @Test
    @DisplayName("linkTossSymbol — 프로(SECURITIES) 미구독은 연결 불가")
    void linkRejectsNonPro() {
        willThrow(new ForbiddenException(DeskErrorCode.SUBSCRIPTION_REQUIRED))
                .given(entitlementService).requireFeature(USER_ID, "SECURITIES");

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 토스 미연결 사용자는 연결 불가")
    void linkRejectsTossNotConnected() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — symbol 이 비어있으면 거부")
    void linkRejectsBlankSymbol() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "  ", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — INVESTMENT 가 아닌 자산은 연결 불가")
    void linkRejectsNonInvestment() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 남의 자산은 연결 불가")
    void linkRejectsOthers() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 게이트 통과 + INVESTMENT 면 종목을 연결한다")
    void linkSuccess() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.INVESTMENT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(priceProviders.forUser(USER_ID)).willReturn(priceProvider);
        given(priceProvider.getPrices(USER_ID, List.of(InstrumentRef.of("005930")))).willReturn(List.of(
                PriceQuote.of("005930", new java.math.BigDecimal("70000"), "KRW")));

        sut.linkSymbol(5L, USER_ID, "005930", 10L);

        verify(asset).linkSecurities("005930", 10L);
        // 연결 즉시 평가액 스냅샷 (70000 × 10).
        verify(balanceHistoryService).recordValuation(eq(asset), eq(700_000L), any());
    }

    @Test
    @DisplayName("linkTossSymbol — 토스가 시세를 못 주는 종목은 거부")
    void linkRejectsInvalidSymbol() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.INVESTMENT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(priceProviders.forUser(USER_ID)).willReturn(priceProvider);
        given(priceProvider.getPrices(USER_ID, List.of(InstrumentRef.of("999999")))).willReturn(List.of());

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "999999", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 보유수량이 0 이하면 거부")
    void linkRejectsNonPositiveQuantity() {
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.linkSymbol(5L, USER_ID, "005930", 0L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("unlinkTossSymbol — 남의 자산은 해제 불가")
    void unlinkRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.unlinkSymbol(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("unlinkTossSymbol — 본인 자산은 연결을 해제한다(구독 없이도 가능)")
    void unlinkSuccess() {
        Asset asset = assetOwnedBy(USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        sut.unlinkSymbol(5L, USER_ID);

        verify(asset).unlinkSecurities();
    }

    @Test
    @DisplayName("unlinkTossSymbol — 해제 시 마지막 평가액(시세×수량)을 자산 금액으로 굳힌다")
    void unlinkFreezesValuation() {
        Asset asset = mock(Asset.class);
        given(asset.getUser()).willReturn(user(USER_ID));
        given(asset.isSecuritiesLinked()).willReturn(true);
        given(asset.getSymbol()).willReturn("005930");
        given(asset.getQuantity()).willReturn(10L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(priceProviders.forUser(USER_ID)).willReturn(priceProvider);
        given(priceProvider.getPrices(USER_ID, List.of(InstrumentRef.of("005930")))).willReturn(List.of(
                PriceQuote.of("005930", new java.math.BigDecimal("70000"), "KRW")));

        sut.unlinkSymbol(5L, USER_ID);

        // 굳히는 값도 '보유 평가금액' 이라 HOLDING 채널로 간다 — CASH 로 찍으면
        // 예수금이 평가액만큼 부풀고 기존 HOLDING 앵커와 이중으로 더해진다.
        verify(balanceHistoryService).recordValuation(eq(asset), eq(700_000L), any());
        verify(asset).unlinkSecurities();
    }

    // === 토스 평가액 스냅샷 (추이) ===

    private Asset tossLinked(long ownerRowId, String symbol, long quantity) {
        Asset a = mock(Asset.class);
        given(a.getUser()).willReturn(user(ownerRowId));
        given(a.isSecuritiesLinked()).willReturn(true);
        given(a.getSymbol()).willReturn(symbol);
        given(a.getQuantity()).willReturn(quantity);
        return a;
    }

    @Test
    @DisplayName("snapshotSecuritiesValuations — 시세×수량을 VALUATION 으로 적재")
    void snapshotRecordsValuation() {
        Asset linked = tossLinked(USER_ID, "005930", 10L);
        given(assetRepository.findAllByType(com.porest.desk.asset.type.AssetType.INVESTMENT))
                .willReturn(List.of(linked));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(true);
        given(securitiesCredentialService.hasAnyConnection(USER_ID)).willReturn(true);
        given(priceProviders.forUser(USER_ID)).willReturn(priceProvider);
        given(priceProvider.getPrices(USER_ID, List.of(InstrumentRef.of("005930")))).willReturn(List.of(
                PriceQuote.of("005930", new java.math.BigDecimal("70000"), "KRW")));

        sut.snapshotSecuritiesValuations();

        verify(balanceHistoryService).recordValuation(eq(linked), eq(700_000L), any());
    }

    @Test
    @DisplayName("snapshotSecuritiesValuations — 프로 미보유 사용자는 스냅샷하지 않음")
    void snapshotSkipsNonPro() {
        Asset linked = mock(Asset.class);
        given(linked.getUser()).willReturn(user(USER_ID));
        given(linked.isSecuritiesLinked()).willReturn(true);
        given(assetRepository.findAllByType(com.porest.desk.asset.type.AssetType.INVESTMENT))
                .willReturn(List.of(linked));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(false);

        sut.snapshotSecuritiesValuations();

        verify(balanceHistoryService, never()).recordValuation(any(), anyLong(), any());
    }

    @Nested
    @DisplayName("이체 수수료 부호 — 음수면 없던 돈이 생긴다")
    class TransferFeeSign {

        @Test
        @DisplayName("음수 수수료는 거부한다 — 출금은 줄고 입금은 그대로라 돈이 늘어난다")
        void rejectsNegativeFee() {
            User u = user(USER_ID);
            // 수수료 검증이 자산 조회보다 먼저 도므로 자산 스텁은 lenient 로 둔다.
            Asset from = mock(Asset.class);
            Asset to = mock(Asset.class);
            lenient().when(from.getUser()).thenReturn(u);
            lenient().when(to.getUser()).thenReturn(u);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            lenient().when(assetRepository.findById(10L)).thenReturn(Optional.of(from));
            lenient().when(assetRepository.findById(11L)).thenReturn(Optional.of(to));

            // amount 100,000 / fee -50,000 → 출금 -(100,000-50,000)= -50,000, 입금 +100,000.
            // 순자산이 50,000 늘어난다.
            var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 100_000L, -50_000L, 0L, "이체",
                LocalDate.of(2026, 6, 1).atStartOfDay(), null);

            assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("수수료 0 은 정상 — 대부분의 이체가 수수료가 없다")
        void allowsZeroFee() {
            User u = user(USER_ID);
            Asset from = mock(Asset.class);
            Asset to = mock(Asset.class);
            given(from.getUser()).willReturn(u);
            given(to.getUser()).willReturn(u);
            given(from.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
            given(to.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
            given(assetRepository.findById(10L)).willReturn(Optional.of(from));
            given(assetRepository.findById(11L)).willReturn(Optional.of(to));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
            given(assetTransferRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 100_000L, 0L, 0L, "이체",
                LocalDate.of(2026, 6, 1).atStartOfDay(), null);

            org.assertj.core.api.Assertions.assertThatCode(() -> sut.createTransfer(cmd))
                .doesNotThrowAnyException();
        }
    }

    // ── 이체 수정 ──────────────────────────────────────────────────

    private AssetTransfer transferOf(long rowId, Asset from, Asset to, long amount, String autoSource) {
        AssetTransfer t = AssetTransfer.createTransfer(user(USER_ID), from, to, amount, 0L, 0L,
            "이체", LocalDate.of(2026, 6, 1).atStartOfDay());
        ReflectionTestUtils.setField(t, "rowId", rowId);
        if (autoSource != null) {
            t.markAutoGenerated(autoSource);
        }
        return t;
    }

    @Test
    @DisplayName("updateTransfer — 옛 잔액 이력을 걷어내고 새 값으로 다시 만든다")
    void updateTransferRebuildsSideEffects() {
        Asset from = assetOwnedBy(USER_ID);
        ReflectionTestUtils.setField(from, "rowId", 10L);
        Asset to = assetOwnedBy(USER_ID);
        ReflectionTestUtils.setField(to, "rowId", 11L);
        AssetTransfer transfer = transferOf(5L, from, to, 50_000L, null);

        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));
        given(assetRepository.findById(10L)).willReturn(Optional.of(from));
        given(assetRepository.findById(11L)).willReturn(Optional.of(to));

        var cmd = new AssetServiceDto.CreateTransferCommand(
            USER_ID, 10L, 11L, 80_000L, 0L, 0L, "고친 이체",
            LocalDate.of(2026, 6, 2).atStartOfDay(), null);
        sut.updateTransfer(5L, cmd);

        // 필드만 바꾸면 옛 flow 가 남아 잔액이 어긋난다 — 걷어내고 다시 찍어야 한다.
        verify(balanceHistoryService).removeTransfer(5L);
        verify(balanceHistoryService).recordTransfer(transfer);
        assertThat(transfer.getAmount()).isEqualTo(80_000L);
        assertThat(transfer.getRowId()).isEqualTo(5L);  // rowId 는 유지 — 참조가 안 끊긴다
    }

    @Test
    @DisplayName("updateTransfer — 매수가 만든 충당 이체는 못 고친다")
    void updateTransferRejectsAutoGenerated() {
        Asset from = assetOwnedBy(USER_ID);
        Asset to = assetOwnedBy(USER_ID);
        AssetTransfer transfer = transferOf(5L, from, to, 50_000L, "TRADE_SETTLEMENT");
        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));

        var cmd = new AssetServiceDto.CreateTransferCommand(
            USER_ID, 10L, 11L, 80_000L, 0L, 0L, "고침",
            LocalDate.of(2026, 6, 2).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.updateTransfer(5L, cmd))
            .isInstanceOf(InvalidValueException.class);
        // 아무것도 건드리지 않는다
        verify(balanceHistoryService, never()).removeTransfer(anyLong());
    }

    @Test
    @DisplayName("deleteTransferByUser — 시스템이 만든 이체는 사용자가 못 지운다")
    void userDeleteRejectsAutoGenerated() {
        Asset from = assetOwnedBy(USER_ID);
        Asset to = assetOwnedBy(USER_ID);
        AssetTransfer transfer = transferOf(5L, from, to, 50_000L, "TRADE_SETTLEMENT");
        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));

        assertThatThrownBy(() -> sut.deleteTransferByUser(5L, USER_ID))
            .isInstanceOf(InvalidValueException.class);
        assertThat(transfer.getIsDeleted()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("deleteTransfer(내부) — 매수 취소가 충당 이체를 지우는 길은 막지 않는다")
    void internalDeleteAllowsAutoGenerated() {
        Asset from = assetOwnedBy(USER_ID);
        Asset to = assetOwnedBy(USER_ID);
        AssetTransfer transfer = transferOf(5L, from, to, 50_000L, "TRADE_SETTLEMENT");
        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));

        sut.deleteTransfer(5L, USER_ID);

        assertThat(transfer.getIsDeleted()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("updateTransfer — 카드 환급 이체는 못 고친다(고치면 잔액이 다시 양수로 뜬다)")
    void updateTransferRejectsCardRefund() {
        Asset from = assetOwnedBy(USER_ID);
        Asset to = assetOwnedBy(USER_ID);
        AssetTransfer transfer = transferOf(5L, from, to, 228_600L, "CARD_REFUND");
        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));

        var cmd = new AssetServiceDto.CreateTransferCommand(
            USER_ID, 10L, 11L, 80_000L, 0L, 0L, "고침",
            LocalDate.of(2026, 6, 2).atStartOfDay(), null);

        assertThatThrownBy(() -> sut.updateTransfer(5L, cmd))
            .isInstanceOf(InvalidValueException.class)
            // 카드 결제·일반 자동생성과 구분되는 안내가 나가야 한다 — 셋이 같은 메시지면
            // "카드 청구 화면에서 취소하세요" 같은 엉뚱한 안내가 환급에도 나간다
            .hasMessageContaining(DeskErrorCode.ASSET_TRANSFER_CARD_REFUND_READONLY.getMessageKey());
        verify(balanceHistoryService, never()).removeTransfer(anyLong());
    }

    @Test
    @DisplayName("deleteTransferByUser — 카드 환급 이체는 사용자가 못 지운다")
    void userDeleteRejectsCardRefund() {
        Asset from = assetOwnedBy(USER_ID);
        Asset to = assetOwnedBy(USER_ID);
        AssetTransfer transfer = transferOf(5L, from, to, 228_600L, "CARD_REFUND");
        given(assetTransferRepository.findById(5L)).willReturn(Optional.of(transfer));

        assertThatThrownBy(() -> sut.deleteTransferByUser(5L, USER_ID))
            .isInstanceOf(InvalidValueException.class);
        assertThat(transfer.getIsDeleted()).isEqualTo(YNType.N);
    }
}
