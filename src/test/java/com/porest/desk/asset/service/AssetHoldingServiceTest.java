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
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
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
    @Mock private com.porest.desk.securities.service.SecuritiesCredentialService securitiesCredentialService;
    @Mock private com.porest.desk.toss.service.TossQueryService tossQueryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    @org.junit.jupiter.api.BeforeEach
    void stubBalances() {
        // 잔액은 이력 집계로 온다 — 보유 편집 판정이 이 값을 본다.
        lenient().when(balanceHistoryService.balanceAt(any(), any()))
            .thenReturn(AssetBalanceHistoryService.Split.ZERO);
        lenient().when(balanceHistoryService.balancesAt(anyCollection(), any()))
            .thenReturn(java.util.Map.of());
    }

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset investment(long rowId, long ownerRowId) {
        Asset a = Asset.createAsset(user(ownerRowId), "토스증권", AssetType.INVESTMENT, 0L,
            "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private AssetServiceDto.CreateAssetCommand createCommand(
            AssetType type, List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "토스증권", type, 0L, "KRW",
            null, null, null, null, 0,
            YNType.Y, null, null, null, null, holdings);
    }

    private AssetServiceDto.UpdateAssetCommand updateCommand(List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.UpdateAssetCommand(
            null, null, null, null,
            null, null, null, null, null, null, null, null, null, holdings);
    }

    private static AssetServiceDto.HoldingCommand linkedHolding(String symbol, Long qty) {
        return new AssetServiceDto.HoldingCommand(
            null,
            HoldingType.STOCK, true, symbol, qty == null ? null : BigDecimal.valueOf(qty), null, null, null);
    }

    private static AssetServiceDto.HoldingCommand manualHolding(String name, Long value) {
        return new AssetServiceDto.HoldingCommand(
            null,HoldingType.STOCK, false, null, null, name, value, null);
    }

    /** 미연동 보유 + 수량(선택) — 금·코인처럼 시세가 없어도 몇 g·몇 개인지 기록한다. */
    private static AssetServiceDto.HoldingCommand manualHolding(
            HoldingType type, String name, String qty, Long value) {
        return new AssetServiceDto.HoldingCommand(
            null,
            type, false, null, qty == null ? null : new BigDecimal(qty), name, value, null);
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
        assertThat(saved.get(0).getSymbol()).isEqualTo("005930");
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
                List.of(new AssetServiceDto.HoldingCommand(
            null,HoldingType.STOCK, true, null, BigDecimal.valueOf(30), null, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(
            null,HoldingType.STOCK, true, "005930", null, null, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAsset — 수동 보유에 이름/평가액이 없으면 400")
    void createRejectsManualWithoutNameOrValue() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(
            null,HoldingType.STOCK, false, null, null, null, 1_000L, null)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT,
                List.of(new AssetServiceDto.HoldingCommand(
            null,HoldingType.STOCK, false, null, null, "ETF", null, null)))))
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
        AssetHolding old = AssetHolding.create(asset, HoldingType.STOCK, YNType.Y, "005930", BigDecimal.TEN, null, null, 0L, 0);
        given(assetHoldingRepository.findActiveByAsset(5L)).willReturn(List.of(old));

        AssetServiceDto.AssetInfo info = sut.updateAsset(5L, USER_ID, updateCommand(List.of(
            linkedHolding("NVDA", 12L),
            manualHolding("채권", 500_000L)
        )));

        // 기존 보유는 dirty checking 으로 soft delete (save 호출 아님)
        assertThat(old.getIsDeleted()).isEqualTo(YNType.Y);
        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getSymbol()).isEqualTo("NVDA");
        assertThat(captor.getAllValues().get(1).getHoldingName()).isEqualTo("채권");
        assertThat(info.holdings()).hasSize(2);
    }

    @Test
    @DisplayName("updateAsset — holdings=[] 이면 기존 보유 전부 삭제(빈 상태)")
    void updateEmptyClearsHoldings() {
        Asset asset = investment(5L, USER_ID);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));
        AssetHolding old = AssetHolding.create(asset, HoldingType.STOCK, YNType.N, null, null, "ETF", 1_000L, 0L, 0);
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
            a1, HoldingType.STOCK, YNType.Y, "005930", BigDecimal.valueOf(30), null, null, 0L, 0);
        AssetHolding h2 = AssetHolding.create(
            a2, HoldingType.STOCK, YNType.N, null, null, "ETF", 1_000L, 0L, 0);
        given(assetHoldingRepository.findActiveByAssets(anyList())).willReturn(List.of(h1, h2));
        // 목록 잔액은 DB 집계로 온다 — 자산 전체 1쿼리다(N+1 금지).
        given(balanceHistoryService.balancesAt(anyCollection(), any()))
            .willReturn(java.util.Map.of());

        List<AssetServiceDto.AssetInfo> infos = sut.getAssets(USER_ID);

        verify(assetHoldingRepository).findActiveByAssets(List.of(5L, 6L));
        assertThat(infos.get(0).holdings()).hasSize(1);
        assertThat(infos.get(0).holdings().get(0).symbol()).isEqualTo("005930");
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
            null,
                    HoldingType.GOLD, true, "04020000", BigDecimal.ONE, null, null, null)))))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
                new AssetServiceDto.HoldingCommand(
            null,
                    HoldingType.CRYPTO, true, "BTC", BigDecimal.ONE, null, null, null)))))
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
    @DisplayName("투자 평가액을 서버가 산정한다 — 클라이언트가 보낸 balance 를 쓰지 않는다")
    void serverComputesInvestmentBalance() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        // 시세 72,000 × 30주 = 2,160,000 + 미연동 1,870,000 = 4,030,000 (클라 balance 0 은 무시)
        given(tossQueryService.getPrices(eq(USER_ID), anyString())).willReturn(List.of(
            new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("005930", null, "72000", "KRW")));

        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            linkedHolding("005930", 30L),
            manualHolding("해외 ETF 포트폴리오", 1_870_000L)
        )));

        // 평가액은 예수금이 아니라 HOLDING 채널 앵커로 간다 — 예수금은 0 에서 시작한다.
        // (recompute 는 mock 이라 여기서는 앵커에 실린 금액으로 검증한다)
        assertThat(info.cashBalance()).isZero();
        assertThat(capturedValuation()).isEqualTo(4_030_000L);
    }

    @Test
    @DisplayName("소수 수량·외화도 BigDecimal 로 정확히 — 0.1주 × $185.7 × 1383.5원")
    void serverComputesFractionalForeignBalance() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(tossQueryService.getPrices(eq(USER_ID), anyString())).willReturn(List.of(
            new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("AAPL", null, "185.7", "USD")));
        given(tossQueryService.getExchangeRate(eq(USER_ID), anyString(), anyString(), any()))
            .willReturn(new com.porest.desk.toss.dto.TossMarketInfoDto.ExchangeRateResponse(
                "USD", "KRW", "1383.5", null, null, null, null, null));

        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            new AssetServiceDto.HoldingCommand(
            null,
                HoldingType.STOCK, true, "AAPL", new BigDecimal("0.1"), null, null, null)
        )));

        // 185.7 × 1383.5 × 0.1 = 25,691.595 → 25,692 (HALF_UP). double 이면 끝자리가 흔들린다.
        assertThat(capturedValuation()).isEqualTo(25_692L);
    }

    @Test
    @DisplayName("연동 시세를 못 구하면 생성 시 미연동 합만 잡는다 — 부분합으로 왜곡하지 않는다")
    void fallsBackToManualSumWhenPriceUnavailable() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(tossQueryService.getPrices(eq(USER_ID), anyString())).willReturn(List.of());

        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            linkedHolding("005930", 30L),
            manualHolding("해외 ETF 포트폴리오", 1_870_000L)
        )));

        assertThat(capturedValuation()).isEqualTo(1_870_000L);
    }

    @Test
    @DisplayName("holdingType 미지정은 STOCK 으로 본다 — 구버전 클라이언트 하위호환")
    void defaultsToStockWhenTypeOmitted() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            new AssetServiceDto.HoldingCommand(
            null,null, false, null, null, "예전 항목", 1_000L, null)
        )));

        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository).save(captor.capture());
        assertThat(captor.getValue().getHoldingType()).isEqualTo(HoldingType.STOCK);
    }


    @Test
    @DisplayName("금 계좌도 주식과 같다 — 평가액은 HOLDING 앵커, 예수금은 0 에서 시작")
    void goldAccountUsesSameChannelSplit() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        // 한국금거래소 계좌에 금 3.75g(한 돈) 70만원어치. 시세 연동 대상이 아니라 전부 수동.
        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            manualHolding(HoldingType.GOLD, "KRX 금현물", "3.75", 700_000L)
        )));

        // 채널 분기는 holdingType 이 아니라 '투자 자산 + 보유 있음' 이라 금도 같은 경로를 탄다.
        assertThat(info.cashBalance()).isZero();
        assertThat(capturedValuation()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("코인 계좌도 주식과 같다 — 평가액은 HOLDING 앵커, 예수금은 0 에서 시작")
    void cryptoAccountUsesSameChannelSplit() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        // 업비트 계좌에 BTC 0.05개 5,000,000원 + ETH 1.2개 6,000,000원.
        AssetServiceDto.AssetInfo info = sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            manualHolding(HoldingType.CRYPTO, "BTC", "0.05", 5_000_000L),
            manualHolding(HoldingType.CRYPTO, "ETH", "1.2", 6_000_000L)
        )));

        assertThat(info.cashBalance()).isZero();
        assertThat(capturedValuation()).isEqualTo(11_000_000L);
    }

    @Test
    @DisplayName("한 계좌에 주식·금·코인이 섞여도 평가액은 하나의 HOLDING 앵커로 합산된다")
    void mixedHoldingTypesShareOneAnchor() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(tossQueryService.getPrices(eq(USER_ID), anyString())).willReturn(List.of(
            new com.porest.desk.toss.dto.TossMarketDto.PriceResponse("005930", null, "72000", "KRW")));

        sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            linkedHolding("005930", 10L),                                  // 720,000
            manualHolding(HoldingType.GOLD, "KRX 금현물", "3.75", 700_000L), // 700,000
            manualHolding(HoldingType.CRYPTO, "BTC", "0.05", 5_000_000L)    // 5,000,000
        )));

        assertThat(capturedValuation()).isEqualTo(6_420_000L);
    }

    /** 평가액은 예수금이 아니라 HOLDING 채널 앵커로 적재된다 — 그 앵커에 실린 금액을 꺼낸다. */
    private long capturedValuation() {
        org.mockito.ArgumentCaptor<Long> c = org.mockito.ArgumentCaptor.forClass(Long.class);
        then(balanceHistoryService).should().recordValuation(any(), c.capture(), any());
        return c.getValue();
    }

    @Test
    @DisplayName("음수 평가액 보유는 거부한다 — 투자 자산이 부(−)로 뒤집힌다")
    void rejectsNegativeHoldingValue() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(
            manualHolding("해외 ETF", -1_870_000L)
        )))).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("음수 매수원가는 거부한다 — 매도 시 실현손익이 부풀려진다")
    void rejectsNegativeTotalCost() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var holding = new AssetServiceDto.HoldingCommand(
            null,
            HoldingType.STOCK, false, null, new BigDecimal("10"), "삼성전자", 700_000L, -500_000L);

        assertThatThrownBy(() -> sut.createAsset(createCommand(AssetType.INVESTMENT, List.of(holding))))
            .isInstanceOf(InvalidValueException.class);
    }
}
