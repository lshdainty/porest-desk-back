package com.porest.desk.asset.service;

import com.porest.core.exception.ForbiddenException;
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
import com.porest.desk.toss.credential.service.TossCredentialService;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
    @Mock private TossCredentialService tossCredentialService;
    @Mock private com.porest.desk.toss.service.TossQueryService tossQueryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;

    private TossCredentialService.CredentialStatus connected() {
        return new TossCredentialService.CredentialStatus(true, true, null);
    }

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset assetOwnedBy(long ownerRowId) {
        Asset a = mock(Asset.class);
        given(a.getUser()).willReturn(user(ownerRowId));
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
                USER_ID, 10L, 11L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());

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
                USER_ID, 10L, 11L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 같은 자산으로의 이체는 불가")
    void transferRejectsSameAsset() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 10L, 50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTransfer — 이체 금액이 0 이하면 불가(음수 자금 역류 방지)")
    void transferRejectsNonPositiveAmount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, -50_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());

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
                USER_ID, 10L, 11L, 30_000L, 1_000L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());
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
                USER_ID, 10L, 11L, 30_000L, 0L, 0L, "이체", LocalDate.of(2026, 6, 1).atStartOfDay());

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
                USER_ID, 10L, 11L, 30_000L, 0L, 0L, "카드 결제", LocalDate.of(2026, 6, 1).atStartOfDay());
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

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 토스 미연결 사용자는 연결 불가")
    void linkRejectsTossNotConnected() {
        given(tossCredentialService.getStatus(USER_ID))
                .willReturn(TossCredentialService.CredentialStatus.notConnected());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — symbol 이 비어있으면 거부")
    void linkRejectsBlankSymbol() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "  ", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — INVESTMENT 가 아닌 자산은 연결 불가")
    void linkRejectsNonInvestment() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 남의 자산은 연결 불가")
    void linkRejectsOthers() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "005930", 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 게이트 통과 + INVESTMENT 면 종목을 연결한다")
    void linkSuccess() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.INVESTMENT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(tossQueryService.getPrices(USER_ID, "005930")).willReturn(List.of(
                new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("005930", null, "70000", "KRW")));

        sut.linkTossSymbol(5L, USER_ID, "005930", 10L);

        verify(asset).linkToss("005930", 10L);
        // 연결 즉시 평가액 스냅샷 (70000 × 10).
        verify(balanceHistoryService).recordValuation(eq(asset), eq(700_000L), any());
    }

    @Test
    @DisplayName("linkTossSymbol — 토스가 시세를 못 주는 종목은 거부")
    void linkRejectsInvalidSymbol() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.INVESTMENT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(tossQueryService.getPrices(USER_ID, "999999")).willReturn(List.of());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "999999", 10L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 보유수량이 0 이하면 거부")
    void linkRejectsNonPositiveQuantity() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, "005930", 0L))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("unlinkTossSymbol — 남의 자산은 해제 불가")
    void unlinkRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.unlinkTossSymbol(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("unlinkTossSymbol — 본인 자산은 연결을 해제한다(구독 없이도 가능)")
    void unlinkSuccess() {
        Asset asset = assetOwnedBy(USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        sut.unlinkTossSymbol(5L, USER_ID);

        verify(asset).unlinkToss();
    }

    @Test
    @DisplayName("unlinkTossSymbol — 해제 시 마지막 평가액(시세×수량)을 자산 금액으로 굳힌다")
    void unlinkFreezesValuation() {
        Asset asset = mock(Asset.class);
        given(asset.getUser()).willReturn(user(USER_ID));
        given(asset.isTossLinked()).willReturn(true);
        given(asset.getTossSymbol()).willReturn("005930");
        given(asset.getTossQuantity()).willReturn(10L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(tossQueryService.getPrices(USER_ID, "005930")).willReturn(List.of(
                new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("005930", null, "70000", "KRW")));

        sut.unlinkTossSymbol(5L, USER_ID);

        // 굳히는 값도 '보유 평가금액' 이라 HOLDING 채널로 간다 — CASH 로 찍으면
        // 예수금이 평가액만큼 부풀고 기존 HOLDING 앵커와 이중으로 더해진다.
        verify(balanceHistoryService).recordValuation(eq(asset), eq(700_000L), any());
        verify(asset).unlinkToss();
    }

    // === 토스 평가액 스냅샷 (추이) ===

    private Asset tossLinked(long ownerRowId, String symbol, long quantity) {
        Asset a = mock(Asset.class);
        given(a.getUser()).willReturn(user(ownerRowId));
        given(a.isTossLinked()).willReturn(true);
        given(a.getTossSymbol()).willReturn(symbol);
        given(a.getTossQuantity()).willReturn(quantity);
        return a;
    }

    @Test
    @DisplayName("snapshotTossValuations — 시세×수량을 VALUATION 으로 적재")
    void snapshotRecordsValuation() {
        Asset linked = tossLinked(USER_ID, "005930", 10L);
        given(assetRepository.findAllByType(com.porest.desk.asset.type.AssetType.INVESTMENT))
                .willReturn(List.of(linked));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(true);
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        given(tossQueryService.getPrices(USER_ID, "005930")).willReturn(List.of(
                new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("005930", null, "70000", "KRW")));

        sut.snapshotTossValuations();

        verify(balanceHistoryService).recordValuation(eq(linked), eq(700_000L), any());
    }

    @Test
    @DisplayName("snapshotTossValuations — 프로 미보유 사용자는 스냅샷하지 않음")
    void snapshotSkipsNonPro() {
        Asset linked = mock(Asset.class);
        given(linked.getUser()).willReturn(user(USER_ID));
        given(linked.isTossLinked()).willReturn(true);
        given(assetRepository.findAllByType(com.porest.desk.asset.type.AssetType.INVESTMENT))
                .willReturn(List.of(linked));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(false);

        sut.snapshotTossValuations();

        verify(balanceHistoryService, never()).recordValuation(any(), anyLong(), any());
    }
}
