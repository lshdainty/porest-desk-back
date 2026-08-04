package com.porest.desk.asset.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 투자 자산 다중 보유(holdings) 프로세스 단위 테스트 —
 * 생성·교체(null=무변경/리스트=전체 교체)·linked 별 필수값 검증·목록 일괄 로딩.
 */
@ExtendWith(MockitoExtension.class)
class AssetHoldingServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHoldingRepository assetHoldingRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private com.porest.desk.subscription.service.SubscriptionEntitlementService entitlementService;
    @Mock private com.porest.desk.toss.credential.service.TossCredentialService tossCredentialService;
    @Mock private com.porest.desk.toss.service.TossQueryService tossQueryService;
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

    private Asset investment(long rowId, long ownerRowId) {
        Asset a = Asset.createAsset(user(ownerRowId), "토스증권", AssetType.INVESTMENT, 0L,
            "KRW", null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private AssetServiceDto.CreateAssetCommand createCommand(
            AssetType type, List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "토스증권", type, 0L, "KRW", null, null, null, 0,
            YNType.Y, null, null, null, null, holdings);
    }

    private AssetServiceDto.UpdateAssetCommand updateCommand(List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.UpdateAssetCommand(
            null, null, null, null, null, null, null, null, null, null, null, null, holdings);
    }

    private static AssetServiceDto.HoldingCommand linkedHolding(String symbol, Long qty) {
        return new AssetServiceDto.HoldingCommand(
            HoldingType.STOCK, true, symbol, qty == null ? null : BigDecimal.valueOf(qty), null, null);
    }

    private static AssetServiceDto.HoldingCommand manualHolding(String name, Long value) {
        return new AssetServiceDto.HoldingCommand(HoldingType.STOCK, false, null, null, name, value);
    }

    /** 미연동 보유 + 수량(선택) — 금·코인처럼 시세가 없어도 몇 g·몇 개인지 기록한다. */
    private static AssetServiceDto.HoldingCommand manualHolding(
            HoldingType type, String name, String qty, Long value) {
        return new AssetServiceDto.HoldingCommand(
            type, false, null, qty == null ? null : new BigDecimal(qty), name, value);
    }

    @Test
    @DisplayName("createAsset — holdings 포함 생성 시 각 항목이 sortOrder=배열 인덱스로 저장된다")
    void createWithHoldings() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            linkedHolding("005930", 30L),
            manualHolding("해외 ETF 포트폴리오", 1_870_000L)
        )));

        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<AssetHolding> saved = captor.getAllValues();
        assertThat(saved.get(0).getLinked()).isEqualTo(YNType.Y);
        assertThat(saved.get(0).getTossSymbol()).isEqualTo("005930");
        assertThat(saved.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0);
        assertThat(saved.get(1).getLinked()).isEqualTo(YNType.N);
        assertThat(saved.get(1).getHoldingName()).isEqualTo("해외 ETF 포트폴리오");
        assertThat(saved.get(1).getHoldingValue()).isEqualTo(1_870_000L);
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        assertThat(info.holdings()).hasSize(2);
    }

    @Test
    @DisplayName("createAsset — linked 보유에 종목코드/수량이 없으면 400")
    void createRejectsLinkedWithoutSymbol() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(HoldingType.STOCK, true, null, BigDecimal.valueOf(30), null, null)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(HoldingType.STOCK, true, "005930", null, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAsset — 수동 보유에 이름/평가액이 없으면 400")
    void createRejectsManualWithoutNameOrValue() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(HoldingType.STOCK, false, null, null, null, 1_000L)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(HoldingType.STOCK, false, null, null, "ETF", null)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAsset — INVESTMENT 가 아닌 자산에 holdings 지정 시 400")
    void createRejectsHoldingsOnNonInvestment() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.BANK_ACCOUNT,
                List.of(linkedHolding("005930", 30L)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAsset — holdings=null 이면 보유를 교체하지 않는다(무변경)")
    void updateNullKeepsHoldings() {
        Asset asset = investment(5L, USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        given(assetHoldingRepository.findActiveByAsset(5L)).willReturn(List.of());

        sut.updateAsset(5L, USER_ID, updateCommand(null));

        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAsset — holdings 리스트면 기존 활성 보유를 soft delete 하고 새 리스트로 교체")
    void updateReplacesHoldings() {
        Asset asset = investment(5L, USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        AssetHolding old = AssetHolding.create(asset, HoldingType.STOCK, YNType.Y, "005930", BigDecimal.TEN, null, null, 0);
        given(assetHoldingRepository.findActiveByAsset(5L)).willReturn(List.of(old));

        AssetServiceDto.AssetInfo info = sut.updateAsset(5L, USER_ID, updateCommand(List.of(
            linkedHolding("NVDA", 12L),
            manualHolding("채권", 500_000L)
        )));

        // 기존 보유는 dirty checking 으로 soft delete (save 호출 아님)
        assertThat(old.getIsDeleted()).isEqualTo(YNType.Y);
        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getTossSymbol()).isEqualTo("NVDA");
        assertThat(captor.getAllValues().get(1).getHoldingName()).isEqualTo("채권");
        assertThat(info.holdings()).hasSize(2);
    }

    @Test
    @DisplayName("updateAsset — holdings=[] 이면 기존 보유 전부 삭제(빈 상태)")
    void updateEmptyClearsHoldings() {
        Asset asset = investment(5L, USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        AssetHolding old = AssetHolding.create(asset, HoldingType.STOCK, YNType.N, null, null, "ETF", 1_000L, 0);
        given(assetHoldingRepository.findActiveByAsset(5L)).willReturn(List.of(old));

        AssetServiceDto.AssetInfo info = sut.updateAsset(5L, USER_ID, updateCommand(List.of()));

        assertThat(old.getIsDeleted()).isEqualTo(YNType.Y);
        verify(assetHoldingRepository, never()).save(any());
        assertThat(info.holdings()).isEmpty();
    }

    @Test
    @DisplayName("getAssets — 목록의 보유를 in-query 1회로 일괄 로딩해 매핑한다(N+1 금지)")
    void getAssetsBatchLoadsHoldings() {
        Asset a1 = investment(5L, USER_ID);
        Asset a2 = investment(6L, USER_ID);
        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(a1, a2));
        AssetHolding h1 = AssetHolding.create(
            a1, HoldingType.STOCK, YNType.Y, "005930", BigDecimal.valueOf(30), null, null, 0);
        AssetHolding h2 = AssetHolding.create(
            a2, HoldingType.STOCK, YNType.N, null, null, "ETF", 1_000L, 0);
        given(assetHoldingRepository.findActiveByAssets(anyList())).willReturn(List.of(h1, h2));

        List<AssetServiceDto.AssetInfo> infos = sut.getAssets(USER_ID);

        verify(assetHoldingRepository).findActiveByAssets(List.of(5L, 6L));
        assertThat(infos.get(0).holdings()).hasSize(1);
        assertThat(infos.get(0).holdings().get(0).tossSymbol()).isEqualTo("005930");
        assertThat(infos.get(1).holdings()).hasSize(1);
        assertThat(infos.get(1).holdings().get(0).holdingName()).isEqualTo("ETF");
    }

    @Test
    @DisplayName("미연동 보유도 수량을 저장한다 — 시세가 없어도 몇 주·몇 g 인지는 남는다")
    void manualHoldingKeepsQuantity() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            manualHolding(HoldingType.STOCK, "비상장 주식", "12", 500_000L)
        )));

        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getLinked()).isEqualTo(YNType.N);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(captor.getValue().getHoldingValue()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("금·코인은 소수 수량을 그대로 보존한다 (3.75g · 0.05 BTC)")
    void goldAndCryptoKeepFractionalQuantity() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            manualHolding(HoldingType.GOLD, "KRX 금현물", "3.75", 700_000L),
            manualHolding(HoldingType.CRYPTO, "비트코인", "0.05", 9_000_000L)
        )));

        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        AssetHolding gold = captor.getAllValues().get(0);
        AssetHolding crypto = captor.getAllValues().get(1);
        assertThat(gold.getHoldingType()).isEqualTo(HoldingType.GOLD);
        assertThat(gold.getQuantity()).isEqualByComparingTo(new BigDecimal("3.75"));
        assertThat(crypto.getHoldingType()).isEqualTo(HoldingType.CRYPTO);
        assertThat(crypto.getQuantity()).isEqualByComparingTo(new BigDecimal("0.05"));
    }

    @Test
    @DisplayName("금·코인은 토스 연동 대상이 아니다 — linked=true 면 400")
    void rejectsLinkedForNonStock() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
                new AssetServiceDto.HoldingCommand(
                    HoldingType.GOLD, true, "04020000", BigDecimal.ONE, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
                new AssetServiceDto.HoldingCommand(
                    HoldingType.CRYPTO, true, "BTC", BigDecimal.ONE, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("음수 수량은 유형·연동 여부와 무관하게 400")
    void rejectsNegativeQuantity() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
                manualHolding(HoldingType.CRYPTO, "비트코인", "-0.05", 1_000L)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("holdingType 미지정은 STOCK 으로 본다 — 구버전 클라이언트 하위호환")
    void defaultsToStockWhenTypeOmitted() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            new AssetServiceDto.HoldingCommand(null, false, null, null, "예전 항목", 1_000L)
        )));

        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getHoldingType()).isEqualTo(HoldingType.STOCK);
    }
}
