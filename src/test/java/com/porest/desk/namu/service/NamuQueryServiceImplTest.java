package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.StockMasterResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 나무 계좌·잔고·환율.
 *
 * <p>국내와 해외는 나무 쪽 엔드포인트도 필드명도 달라, <b>그 차이가 서비스에서 한 모양으로
 * 합쳐지는지</b>를 본다. 환율은 잔고 응답에 얹혀 오는 값이라 계좌가 없으면 못 구한다 —
 * 그때 예외로 터지지 않고 null 로 접히는 것도 함께 지킨다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuQueryServiceImplTest {

    private static final long USER = 3L;
    private static final String ACCT = "12345678-01";

    @Mock private NamuApiClient namuApiClient;
    @Mock private StockMasterRepository stockMasterRepository;
    private NamuQueryServiceImpl sut;

    // 설정은 목이 아니라 진짜를 쓴다 — 목이면 TTL·예산이 0 이라 캐시가 죽은 채로 통과한다.
    @BeforeEach
    void setUpService() {
        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl("https://api.nhplug.com:8443");
        sut = new NamuQueryServiceImpl(namuApiClient,
            new StockMasterResolver(stockMasterRepository), properties);
    }

    private void givenAccounts(NamuAccountDto.Account... accounts) {
        given(namuApiClient.<NamuAccountDto.Account>postList(eq(USER), eq("/n2/acctinfo"), any(), any()))
            .willReturn(List.of(accounts));
    }

    private void givenKrBalance(NamuAccountDto.KrBalanceSummary summary, NamuAccountDto.KrHolding... items) {
        given(namuApiClient.<NamuPagedEnvelope<NamuAccountDto.KrBalanceSummary, NamuAccountDto.KrHolding>>exchange(
                eq(USER), eq("/krstock/inquiry/v1/balance"), any(), any(ParameterizedTypeReference.class)))
            .willReturn(new NamuPagedEnvelope<>("00000", "ok", summary, List.of(items)));
    }

    private void givenGbBalance(NamuAccountDto.GbBalanceSummary summary, NamuAccountDto.GbHolding... items) {
        given(namuApiClient.<NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding>>exchange(
                eq(USER), eq("/gbstock/inquiry/v1/balance"), any(), any(ParameterizedTypeReference.class)))
            .willReturn(new NamuPagedEnvelope<>("00000", "ok", summary, List.of(items)));
    }

    @Nested
    @DisplayName("시세 다건 — 캐시와 시간 예산")
    class Batch {

        private void givenMaster(String symbol, String country) {
            StockMaster stock = StockMaster.create(MasterSource.KIS, InstrumentRecord.kis(
                "KR".equals(country) ? StockMarket.KOSPI : StockMarket.NAS,
                symbol, null, null, symbol, symbol, StockSecurityType.STOCK,
                "KR".equals(country) ? "KRW" : "USD"));
            given(stockMasterRepository.findAllActiveBySymbol(symbol)).willReturn(List.of(stock));
        }

        @Test
        @DisplayName("같은 종목을 다시 물으면 캐시로 답한다 — 자산 화면이 10초마다 폴링한다")
        void secondCallHitsCache() {
            givenMaster("005930", "KR");
            given(namuApiClient.<NamuMarketDto.KrPrice>postObject(
                    eq(USER), eq("/krstock/quote/v1/currentPrice"), any(), any()))
                .willReturn(new NamuMarketDto.KrPrice("70000", "69500", "2", "500", "0.72"));

            List<InstrumentRef> refs = List.of(InstrumentRef.of("005930"));
            assertThat(sut.getPrices(USER, refs)).hasSize(1);
            assertThat(sut.getPrices(USER, refs)).hasSize(1);

            // 업스트림은 한 번만 갔다 — 나무엔 다건 시세 API 가 없어 이게 중요하다.
            verify(namuApiClient, times(1)).postObject(
                eq(USER), eq("/krstock/quote/v1/currentPrice"), any(), any());
        }

        @Test
        @DisplayName("사용자가 다르면 캐시를 공유하지 않는다 — 남의 키로 받은 시세다")
        void cacheIsPerUser() {
            givenMaster("005930", "KR");
            given(namuApiClient.<NamuMarketDto.KrPrice>postObject(
                    any(Long.class), eq("/krstock/quote/v1/currentPrice"), any(), any()))
                .willReturn(new NamuMarketDto.KrPrice("70000", "69500", "2", "500", "0.72"));

            List<InstrumentRef> refs = List.of(InstrumentRef.of("005930"));
            sut.getPrices(USER, refs);
            sut.getPrices(USER + 1, refs);

            verify(namuApiClient, times(2)).postObject(
                any(Long.class), eq("/krstock/quote/v1/currentPrice"), any(), any());
        }

        @Test
        @DisplayName("한 종목이 실패해도 나머지는 살린다 — 전체를 접으면 평가가 통째로 멈춘다")
        void oneFailureDoesNotSinkTheBatch() {
            givenMaster("005930", "KR");
            givenMaster("000660", "KR");
            given(namuApiClient.<NamuMarketDto.KrPrice>postObject(
                    eq(USER), eq("/krstock/quote/v1/currentPrice"), any(), any()))
                .willThrow(new IllegalStateException("boom"))
                .willReturn(new NamuMarketDto.KrPrice("120000", "119000", "2", "1000", "0.84"));

            assertThat(sut.getPrices(USER,
                List.of(InstrumentRef.of("005930"), InstrumentRef.of("000660")))).hasSize(1);
        }

        @Test
        @DisplayName("마스터에 없는 종목은 건너뛰고 업스트림을 부르지 않는다")
        void unknownSymbolIsSkipped() {
            given(stockMasterRepository.findAllActiveBySymbol("NOPE")).willReturn(List.of());

            assertThat(sut.getPrices(USER, List.of(InstrumentRef.of("NOPE")))).isEmpty();
            verify(namuApiClient, never()).postObject(any(Long.class), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("시세 — 전일 종가")
    class PreviousClose {

        private void givenKrPrice(NamuMarketDto.KrPrice p) {
            given(namuApiClient.<NamuMarketDto.KrPrice>postObject(
                    eq(USER), eq("/krstock/quote/v1/currentPrice"), any(), any()))
                .willReturn(p);
        }

        @Test
        @DisplayName("응답의 전일 종가를 그대로 쓴다 — 전일대비로 역산하지 않는다")
        void usesDirectField() {
            // 부호코드를 '하락'(5)으로 줘도 전일 종가 필드가 이기는지 본다.
            givenKrPrice(new NamuMarketDto.KrPrice("70000", "69500", "5", "500", "0.72"));

            assertThat(sut.getKrPrice(USER, "005930", "KRX").previousClose())
                .isEqualByComparingTo(new BigDecimal("69500"));
        }

        @Test
        @DisplayName("전일 종가가 비면 전일대비로 역산한다 — 상승(2)이면 현재가에서 뺀다")
        void fallsBackToSignedChange() {
            givenKrPrice(new NamuMarketDto.KrPrice("70000", "", "2", "500", "0.72"));

            assertThat(sut.getKrPrice(USER, "005930", "KRX").previousClose())
                .isEqualByComparingTo(new BigDecimal("69500"));
        }

        @Test
        @DisplayName("하락(5)이면 현재가에 더한다")
        void fallbackHandlesDown() {
            givenKrPrice(new NamuMarketDto.KrPrice("70000", null, "5", "500", "-0.71"));

            assertThat(sut.getKrPrice(USER, "005930", "KRX").previousClose())
                .isEqualByComparingTo(new BigDecimal("70500"));
        }

        @Test
        @DisplayName("모르는 부호코드면 방향을 찍지 않고 비운다 — 찍었다 틀리면 등락이 뒤집힌다")
        void unknownSignYieldsNull() {
            givenKrPrice(new NamuMarketDto.KrPrice("70000", null, "9", "500", "0.72"));

            assertThat(sut.getKrPrice(USER, "005930", "KRX").previousClose()).isNull();
        }

        @Test
        @DisplayName("해외도 base_prc 를 그대로 쓰고 통화를 함께 싣는다")
        void overseasUsesBasePrice() {
            given(namuApiClient.<NamuMarketDto.GbPrice>postObject(
                    eq(USER), eq("/gbstock/quote/v1/current"), any(), any()))
                .willReturn(new NamuMarketDto.GbPrice("185.70", "184.00", "2", "1.70", "0.92", "USD"));

            PriceQuote q = sut.getGbPrice(USER, "AAPL");

            assertThat(q.currency()).isEqualTo("USD");
            assertThat(q.previousClose()).isEqualByComparingTo(new BigDecimal("184.00"));
        }
    }

    @Nested
    @DisplayName("보유 종목")
    class Holdings {

        @Test
        @DisplayName("국내 — 잔고 요약과 종목별을 한 모양으로 합친다")
        void domestic() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenKrBalance(
                new NamuAccountDto.KrBalanceSummary("1000000", "900000", "1000000", "100000", "11.1", "50000"),
                new NamuAccountDto.KrHolding("005930", "삼성전자", "10", "65000", "70000", "700000", "50000"));

            NamuAccountDto.Holdings h = sut.getHoldings(USER, null, "KRW");

            assertThat(h.accountNo()).isEqualTo(ACCT);
            assertThat(h.currency()).isEqualTo("KRW");
            assertThat(h.totalEvalAmount()).isEqualTo("1000000");
            assertThat(h.items()).singleElement().satisfies(i -> {
                assertThat(i.symbol()).isEqualTo("005930");
                assertThat(i.name()).isEqualTo("삼성전자");
                assertThat(i.quantity()).isEqualTo("10");
            });
        }

        @Test
        @DisplayName("해외 — 한글명이 비면 영문명으로 채운다. 목록에 빈 칸이 남으면 안 된다")
        void overseasFallsBackToEnglishName() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenGbBalance(
                new NamuAccountDto.GbBalanceSummary("2000000", "1500", "300", "20.0"),
                new NamuAccountDto.GbHolding("AAPL", "", "APPLE INC", "5", "180", "185.7", "928.5", "50", "1284000", "USD", "1383.50"));

            NamuAccountDto.Holdings h = sut.getHoldings(USER, ACCT, "USD");

            assertThat(h.currency()).isEqualTo("USD");
            assertThat(h.items()).singleElement()
                .extracting(NamuAccountDto.HoldingItem::name).isEqualTo("APPLE INC");
            // 요약도 종목과 같은 외화 기준이어야 한다 — 원화 합을 섞으면 USD 를 붙여 보여주게 된다.
            assertThat(h.totalEvalAmount()).isEqualTo("1500");
        }

        @Test
        @DisplayName("계좌가 없으면 빈 잔고 — 예외로 터뜨리지 않는다")
        void noAccount() {
            givenAccounts();

            NamuAccountDto.Holdings h = sut.getHoldings(USER, null, "KRW");

            assertThat(h.items()).isEmpty();
            assertThat(h.totalEvalAmount()).isEqualTo("0");
        }

        @Test
        @DisplayName("계좌를 넘겨받으면 목록을 다시 부르지 않는다")
        void explicitAccountSkipsLookup() {
            givenKrBalance(null);

            sut.getHoldings(USER, ACCT, "KRW");

            verify(namuApiClient, never()).postList(anyLong(), eq("/n2/acctinfo"), any(), any());
        }
    }

    @Nested
    @DisplayName("환율")
    class Fx {

        @Test
        @DisplayName("환율은 종목 행(Output_1)에서 읽는다 — 계좌 요약엔 없다")
        void fromHoldingRow() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenGbBalance(new NamuAccountDto.GbBalanceSummary("0", "0", "0", "0"),
                new NamuAccountDto.GbHolding("AAPL", "애플", "APPLE INC", "5", "180", "185.7",
                    "928.5", "28.5", "1284000", "USD", "1383.50"));

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
        }

        @Test
        @DisplayName("같은 통화 보유가 없으면 null — 종목마다 통화가 달라 계좌 요약이 환율을 못 든다")
        void nullWhenCurrencyNotHeld() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenGbBalance(new NamuAccountDto.GbBalanceSummary("0", "0", "0", "0"),
                new NamuAccountDto.GbHolding("7203", "도요타", "TOYOTA", "10", "2000", "2100",
                    "21000", "1000", "200000", "JPY", "9.12"));

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }

        @Test
        @DisplayName("계좌가 없으면 null — 호출부가 외화 평가를 접는다")
        void nullWhenNoAccount() {
            givenAccounts();

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }

        @Test
        @DisplayName("잔고 조회가 실패해도 null 로 접힌다 — 환율 하나 때문에 평가 전체가 죽으면 안 된다")
        void nullWhenBalanceFails() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            willThrow(new IllegalStateException("boom"))
                .given(namuApiClient).exchange(eq(USER), eq("/gbstock/inquiry/v1/balance"), any(), any());

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }

        @Test
        @DisplayName("보유 종목이 없으면 null")
        void nullWhenNoHoldings() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenGbBalance(null);

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }
    }

    @Test
    @DisplayName("계좌목록조회는 입력이 없어도 Input_0 봉투를 지킨다")
    void accountsKeepEnvelope() {
        givenAccounts(new NamuAccountDto.Account(ACCT, "01"));

        assertThat(sut.getAccounts(USER)).hasSize(1);
        verify(namuApiClient).postList(eq(USER), eq("/n2/acctinfo"), eq(Map.of()), any());
    }
}
