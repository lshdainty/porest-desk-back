package io.groo.damda.asset.service;

import io.groo.core.type.YNType;
import io.groo.damda.asset.domain.Asset;
import io.groo.damda.asset.repository.AssetRepository;
import io.groo.damda.asset.repository.AssetTransferRepository;
import io.groo.damda.asset.service.dto.AssetServiceDto;
import io.groo.damda.asset.type.AssetType;
import io.groo.damda.card.repository.CardCatalogRepository;
import io.groo.damda.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import io.groo.core.time.ServiceClock;
import io.groo.core.time.UserClock;

/**
 * 외화 투자 자산의 원화 합계 — 예수금(자산 통화)만 환산하고 평가금액(저장 때 이미 원화)은 그대로 더한다.
 *
 * <p>종전에는 둘을 더한 값에 환율을 곱해 평가금액이 환율 배로 부풀었다
 * (USD 환율 1,300 · 평가 10,000,000원 → 13,000,000,000원, 지난달 대비 −99.9%).
 * 요약 · 지난달 · 추이가 같은 식을 쓰는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class ForeignInvestTotalTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    // 시장코드 확정은 mock 기본값(null) — 확정 못 한 경우와 같다.

    @Mock private io.groo.damda.stock.service.StockMasterResolver stockMasterResolver;
    // 회차별 결제일(D5) — 이력이 비어 있으면 카드의 지금 결제일 하나로 센다. 닫힌 회차의 "오늘"은 서울 시계(D11).
    private final io.groo.damda.asset.repository.AssetPaymentDayHistoryRepository paymentDayHistory =
        org.mockito.Mockito.mock(io.groo.damda.asset.repository.AssetPaymentDayHistoryRepository.class);
    @org.mockito.Spy private io.groo.damda.card.service.PaymentScheduleService paymentScheduleService =
        new io.groo.damda.card.service.PaymentScheduleService(paymentDayHistory);
    @org.mockito.Spy private io.groo.core.time.ServiceClock serviceClock =
        new io.groo.core.time.ServiceClock("Asia/Seoul");

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;

    private Asset asset(long rowId, AssetType type) {
        Asset a = Asset.createAsset(null, "자산" + rowId, type, 0L, "KRW",
            null,
                null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private Asset foreignAsset(long rowId, AssetType type, String currency, String rate) {
        Asset a = Asset.createAsset(null, currency + "통장", type, 0L, currency,
            new java.math.BigDecimal(rate),
            null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private static AssetBalanceHistoryService.Split split(long cash, long holding) {
        return new AssetBalanceHistoryService.Split(cash, holding);
    }

    /** 이번 달(at) 과 지난달(prev) 집계를 순서대로 돌려준다 — buildSummaryAt 이 at, prev 순으로 부른다. */
    private void stubAtAndPrev(java.util.Map<Long, AssetBalanceHistoryService.Split> at,
                               java.util.Map<Long, AssetBalanceHistoryService.Split> prev) {
        given(balanceHistoryService.balancesAt(anyCollection(), any(LocalDateTime.class)))
            .willReturn(at, prev);
    }

    private AssetServiceDto.AssetSummary summaryOf(Asset a,
                                                   AssetBalanceHistoryService.Split now,
                                                   AssetBalanceHistoryService.Split last) {
        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(a));
        stubAtAndPrev(java.util.Map.of(a.getRowId(), now), java.util.Map.of(a.getRowId(), last));
        return sut.getAssetSummary(USER_ID, null, null);
    }

    @Test
    @DisplayName("USD 투자 자산 — 예수금 $100 · 환율 1,300 · 평가 10,000,000원 = 10,130,000원 (13,000,130,000 아님)")
    void usdInvestmentCashConvertedHoldingAsIs() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(100, 10_000_000L), split(100, 10_000_000L));

        assertThat(s.netWorth()).isEqualTo(10_130_000L);
        assertThat(s.totalAssets()).isEqualTo(10_130_000L);
        assertThat(s.totalBalance()).isEqualTo(10_130_000L);
        assertThat(s.byType()).singleElement().satisfies(t -> assertThat(t.totalBalance()).isEqualTo(10_130_000L));
    }

    @Test
    @DisplayName("지난달 값도 같은 식 — 지난달 평가 9,500,000 이면 지난달 9,630,000, 변화 +500,000")
    void lastMonthUsesSameFormula() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(100, 10_000_000L), split(100, 9_500_000L));

        assertThat(s.netWorth()).isEqualTo(10_130_000L);
        assertThat(s.lastMonthNetWorth()).isEqualTo(9_630_000L);
        assertThat(s.changeAmount()).isEqualTo(500_000L);
        assertThat(s.changePercent()).isEqualTo(5.2);   // 500,000 / 9,630,000 = 5.19% → 5.2
    }

    @Test
    @DisplayName("예수금 0 — 평가금액만 있는 USD 투자 자산은 평가금액 그대로")
    void zeroCash() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(0, 10_000_000L), split(0, 10_000_000L));

        assertThat(s.netWorth()).isEqualTo(10_000_000L);
        assertThat(s.changeAmount()).isZero();
    }

    @Test
    @DisplayName("평가금액 0 — 예수금만 있는 USD 투자 자산은 예수금 × 환율")
    void zeroHolding() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(100, 0L), split(100, 0L));

        assertThat(s.netWorth()).isEqualTo(130_000L);
    }

    @Test
    @DisplayName("둘 다 0 — 0")
    void bothZero() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(0, 0L), split(0, 0L));

        assertThat(s.netWorth()).isZero();
        assertThat(s.changePercent()).isZero();
    }

    @Test
    @DisplayName("투자가 아닌 USD 자산 — 종전과 같다(balance × 환율), 지난달도 같다")
    void nonInvestmentForeignUnchanged() {
        Asset usd = foreignAsset(1L, AssetType.BANK_ACCOUNT, "USD", "1400");

        AssetServiceDto.AssetSummary s = summaryOf(usd, split(1_000, 0L), split(500, 0L));

        assertThat(s.netWorth()).isEqualTo(1_400_000L);
        assertThat(s.lastMonthNetWorth()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("원화 투자 자산 — 환율 1 이라 예수금 + 평가금액 그대로")
    void krwInvestmentUnchanged() {
        Asset inv = asset(1L, AssetType.INVESTMENT);

        AssetServiceDto.AssetSummary s = summaryOf(inv, split(30_000, 10_000_000L), split(30_000, 9_000_000L));

        assertThat(s.netWorth()).isEqualTo(10_030_000L);
        assertThat(s.lastMonthNetWorth()).isEqualTo(9_030_000L);
    }

    @Test
    @DisplayName("반올림 경계 — 예수금 × 환율은 HALF_UP 로 원 단위 반올림한 뒤 평가금액을 더한다")
    void roundingBoundary() {
        // 3 × 1300.5 = 3901.5 → 3902 (HALF_UP), 평가금액은 반올림에 끼지 않는다
        Asset half = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300.5");
        assertThat(summaryOf(half, split(3, 1_000L), split(0, 0L)).netWorth()).isEqualTo(3_902L + 1_000L);
    }

    @Test
    @DisplayName("반올림 경계 — 3901.4 는 내림")
    void roundingDown() {
        // 7 × 557.2 = 3900.4 → 3900
        Asset a = foreignAsset(1L, AssetType.INVESTMENT, "USD", "557.2");
        assertThat(summaryOf(a, split(7, 5L), split(0, 0L)).netWorth()).isEqualTo(3_900L + 5L);
    }

    @Test
    @DisplayName("환율이 0 이하 · 없음 — 생성 때 1 로 정규화돼 예수금은 그대로, 평가금액은 그대로(종전과 같은 처리)")
    void missingOrZeroRateFallsBackToOne() {
        Asset zero = foreignAsset(1L, AssetType.INVESTMENT, "USD", "0");
        assertThat(summaryOf(zero, split(100, 10_000_000L), split(0, 0L)).netWorth()).isEqualTo(10_000_100L);
    }

    @Test
    @DisplayName("원화 + 외화 투자 + 외화 대출 혼합 — 각자 식대로 더해 순자산이 맞는다")
    void mixedPortfolio() {
        Asset krw = asset(1L, AssetType.BANK_ACCOUNT);
        Asset inv = foreignAsset(2L, AssetType.INVESTMENT, "USD", "1300");
        Asset loan = foreignAsset(3L, AssetType.LOAN, "USD", "1300");
        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(krw, inv, loan));
        java.util.Map<Long, AssetBalanceHistoryService.Split> m = new java.util.HashMap<>();
        m.put(1L, split(1_000_000L, 0L));
        m.put(2L, split(100, 10_000_000L));
        m.put(3L, split(-1_000, 0L));
        stubAtAndPrev(m, m);

        AssetServiceDto.AssetSummary s = sut.getAssetSummary(USER_ID, null, null);

        assertThat(s.totalAssets()).isEqualTo(1_000_000L + 10_130_000L);
        assertThat(s.totalDebt()).isEqualTo(1_300_000L);
        assertThat(s.netWorth()).isEqualTo(1_000_000L + 10_130_000L - 1_300_000L);
        assertThat(s.netWorth()).isEqualTo(s.totalBalance());
    }

    @Test
    @DisplayName("순자산 추이 — 점마다 같은 식(예수금만 환산)")
    void trendUsesSameFormula() {
        Asset inv = foreignAsset(1L, AssetType.INVESTMENT, "USD", "1300");
        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(inv));
        given(balanceHistoryService.balancesAtPoints(anyCollection(), anyList()))
            .willReturn(java.util.Map.of(1L, List.of(split(100, 9_500_000L), split(100, 10_000_000L))));

        List<AssetServiceDto.NetWorthTrendPoint> pts = sut.getNetWorthTrend(USER_ID, 2);

        assertThat(pts).hasSize(2);
        assertThat(pts.get(0).netWorth()).isEqualTo(9_630_000L);
        assertThat(pts.get(1).netWorth()).isEqualTo(10_130_000L);
    }
}
