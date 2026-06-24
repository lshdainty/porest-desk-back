package com.porest.desk.asset.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import com.porest.desk.toss.credential.service.TossCredentialService;
import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.service.TossQueryService;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

/**
 * 자산 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 자산/이체는 조회·수정·삭제·이체할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private SubscriptionEntitlementService entitlementService;
    @Mock private TossCredentialService tossCredentialService;
    @Mock private TossQueryService tossQueryService;

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
                USER_ID, 10L, 11L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

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
                USER_ID, 10L, 11L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 같은 자산으로의 이체는 불가")
    void transferRejectsSameAsset() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 10L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTransfer — 이체 금액이 0 이하면 불가(음수 자금 역류 방지)")
    void transferRejectsNonPositiveAmount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, -50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

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
                USER_ID, 10L, 11L, 30_000L, 1_000L, "이체", LocalDate.of(2026, 6, 1));
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

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, 100L, "005930"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 토스 미연결 사용자는 연결 불가")
    void linkRejectsTossNotConnected() {
        given(tossCredentialService.getStatus(USER_ID))
                .willReturn(TossCredentialService.CredentialStatus.notConnected());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, 100L, "005930"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — symbol 이 비어있으면 거부")
    void linkRejectsBlankSymbol() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, 100L, "  "))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — INVESTMENT 가 아닌 자산은 연결 불가")
    void linkRejectsNonInvestment() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, 100L, "005930"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 남의 자산은 연결 불가")
    void linkRejectsOthers() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.linkTossSymbol(5L, USER_ID, 100L, "005930"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("linkTossSymbol — 게이트 통과 + INVESTMENT 면 종목을 연결한다")
    void linkSuccess() {
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        Asset asset = assetOwnedBy(USER_ID);
        given(asset.getAssetType()).willReturn(AssetType.INVESTMENT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        sut.linkTossSymbol(5L, USER_ID, 100L, "005930");

        verify(asset).linkToss(100L, "005930");
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

    // === 토스 평가액 스냅샷 (Phase 1b) ===

    private Asset linkedInvestment(long ownerRowId) {
        Asset a = mock(Asset.class);
        given(a.isTossLinked()).willReturn(true);
        given(a.getUser()).willReturn(user(ownerRowId));
        return a;
    }

    private TossAccountDto.HoldingsOverview holdings(String symbol, String krwAmount) {
        TossAccountDto.MarketValue mv = new TossAccountDto.MarketValue(null, krwAmount, null);
        TossAccountDto.HoldingsItem item = new TossAccountDto.HoldingsItem(
            symbol, "삼성전자", "KR", "KRW", "10", "100000", "90000", mv, null, null, null);
        return new TossAccountDto.HoldingsOverview(null, null, null, null, List.of(item));
    }

    @Test
    @DisplayName("snapshotTossValuations — 프로+토스 연결 사용자의 연결 자산에 평가액을 적재한다")
    void snapshotRecordsValuation() {
        Asset a = linkedInvestment(USER_ID);
        given(a.getTossAccountSeq()).willReturn(100L);
        given(a.getTossSymbol()).willReturn("005930");
        given(assetRepository.findAllByType(AssetType.INVESTMENT)).willReturn(List.of(a));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(true);
        given(tossCredentialService.getStatus(USER_ID)).willReturn(connected());
        given(tossQueryService.getHoldings(USER_ID, 100L, null))
                .willReturn(holdings("005930", "1000000"));

        sut.snapshotTossValuations();

        verify(balanceHistoryService).recordValuation(eq(a), eq(1_000_000L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("snapshotTossValuations — 프로 미구독 사용자는 적재하지 않는다")
    void snapshotSkipsNonPro() {
        Asset a = linkedInvestment(USER_ID);
        given(assetRepository.findAllByType(AssetType.INVESTMENT)).willReturn(List.of(a));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(false);

        sut.snapshotTossValuations();

        verify(balanceHistoryService, never()).recordValuation(any(), anyLong(), any());
    }

    @Test
    @DisplayName("snapshotTossValuations — 토스 미연결 사용자는 적재하지 않는다")
    void snapshotSkipsTossNotConnected() {
        Asset a = linkedInvestment(USER_ID);
        given(assetRepository.findAllByType(AssetType.INVESTMENT)).willReturn(List.of(a));
        given(entitlementService.hasFeature(USER_ID, "SECURITIES")).willReturn(true);
        given(tossCredentialService.getStatus(USER_ID))
                .willReturn(TossCredentialService.CredentialStatus.notConnected());

        sut.snapshotTossValuations();

        verify(balanceHistoryService, never()).recordValuation(any(), anyLong(), any());
    }
}
