package com.porest.desk.stock.service;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 심볼 → 종목 특정.
 *
 * <p>NH 소스가 시장을 6개 늘린 뒤로 같은 티커가 여러 시장에 걸리는 경우가 크게 늘었다
 * (SPY·IVV·JEPI·SOXL 등). 아무거나 고르면 런던 상장 SPY 시세로 미국 보유분을 평가하게 된다.
 * <b>같은 심볼은 어디서 물어도 같은 종목으로 풀려야 한다</b> — 그래서 규칙을 한 곳에 두고
 * 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMasterResolverTest {

    @Mock private StockMasterRepository stockMasterRepository;
    @InjectMocks private StockMasterResolver sut;

    private static StockMaster stock(StockMarket market, String symbol) {
        return StockMaster.create(market.getOwner(), InstrumentRecord.kis(
            market, symbol, null, null, symbol, symbol, StockSecurityType.STOCK, "USD"));
    }

    @Test
    @DisplayName("시장을 알려주면 그걸로 특정한다 — 유일하게 확실한 정보다")
    void exactWhenMarketGiven() {
        StockMaster nys = stock(StockMarket.NYS, "SPY");
        given(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.NYS, "SPY"))
            .willReturn(Optional.of(nys));

        assertThat(sut.resolve(StockMarket.NYS, "SPY")).contains(nys);
    }

    @Test
    @DisplayName("시장을 모르면 KIS 소유 시장을 먼저 본다 — NH 가 시장을 늘리기 전 연결의 의도가 거기 있다")
    void prefersKisOwnedMarket() {
        StockMaster lse = stock(StockMarket.LSE, "SPY");   // NH 소유
        StockMaster nys = stock(StockMarket.NYS, "SPY");   // KIS 소유
        // 저장소가 어떤 순서로 주든 결과가 같아야 한다.
        given(stockMasterRepository.findAllActiveBySymbol("SPY")).willReturn(List.of(lse, nys));

        assertThat(sut.resolve((StockMarket) null, "SPY"))
            .map(StockMaster::getMarketCode).contains(StockMarket.NYS);
    }

    @Test
    @DisplayName("KIS 소유가 여럿이면 국내·미국을 먼저 본다")
    void prefersDomesticAndUs() {
        StockMaster tse = stock(StockMarket.TSE, "1234");
        StockMaster kospi = stock(StockMarket.KOSPI, "1234");
        given(stockMasterRepository.findAllActiveBySymbol("1234")).willReturn(List.of(tse, kospi));

        assertThat(sut.resolve((StockMarket) null, "1234"))
            .map(StockMaster::getMarketCode).contains(StockMarket.KOSPI);
    }

    @Test
    @DisplayName("끝까지 남으면 시장 선언 순서로 고정한다 — DB 반환 순서에 기대면 배포마다 답이 달라진다")
    void deterministicTieBreak() {
        StockMaster ams = stock(StockMarket.AMS, "XYZ");
        StockMaster nas = stock(StockMarket.NAS, "XYZ");
        StockMaster nys = stock(StockMarket.NYS, "XYZ");

        given(stockMasterRepository.findAllActiveBySymbol("XYZ")).willReturn(List.of(ams, nys, nas));
        StockMarket first = sut.resolve((StockMarket) null, "XYZ").orElseThrow().getMarketCode();

        given(stockMasterRepository.findAllActiveBySymbol("XYZ")).willReturn(List.of(nys, nas, ams));
        StockMarket second = sut.resolve((StockMarket) null, "XYZ").orElseThrow().getMarketCode();

        assertThat(first).isEqualTo(second).isEqualTo(StockMarket.NAS); // enum 선언 순서상 NAS 가 앞
    }

    @Test
    @DisplayName("US 안에서도 KIS 소유(NAS/NYS/AMS)가 NH 소유(BTQ/PNK)를 이긴다 — 둘 다 US 라 국가로는 안 갈린다")
    void kisBeatsNhWithinSameCountry() {
        StockMaster btq = stock(StockMarket.BTQ, "ABC");
        StockMaster nas = stock(StockMarket.NAS, "ABC");
        given(stockMasterRepository.findAllActiveBySymbol("ABC")).willReturn(List.of(btq, nas));

        assertThat(sut.resolve((StockMarket) null, "ABC"))
            .map(StockMaster::getMarketCode).contains(StockMarket.NAS);
    }

    @Test
    @DisplayName("문자열 시장코드도 받는다. 모르는 코드는 미지정으로 떨어뜨린다 — 앱이 앞서 나갈 수 있다")
    void unknownMarketStringFallsBack() {
        StockMaster nas = stock(StockMarket.NAS, "AAPL");
        given(stockMasterRepository.findAllActiveBySymbol("AAPL")).willReturn(List.of(nas));

        assertThat(sut.resolve("WHO_KNOWS", "AAPL")).contains(nas);
        assertThat(sut.resolve("", "AAPL")).contains(nas);
    }

    @Test
    @DisplayName("후보가 없거나 심볼이 비면 비어 있다")
    void emptyWhenNoMatch() {
        given(stockMasterRepository.findAllActiveBySymbol("NOPE")).willReturn(List.of());

        assertThat(sut.resolve((StockMarket) null, "NOPE")).isEmpty();
        assertThat(sut.resolve((StockMarket) null, "  ")).isEmpty();
        assertThat(sut.resolve((StockMarket) null, null)).isEmpty();
    }
}
