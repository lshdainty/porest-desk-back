package io.groo.damda.namu.service;

import io.groo.damda.namu.dto.NamuQuoteDto;
import io.groo.damda.securities.service.dto.InvestorTradingDay;
import io.groo.damda.stock.type.StockMarket;
import io.groo.damda.stock.type.StockSecurityType;
import io.groo.damda.toss.dto.TossMarketDto;
import io.groo.damda.toss.dto.TossStockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static io.groo.damda.namu.service.NamuUpstreamFixture.respond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 13b-0 — 나무 종목 상세 · 체결 · 투자자별 · 시간외 · ETF · 증거금. 가짜 상류(MockRestServiceServer)로
 * 문서의 응답 모양을 그대로 태운다. <b>시계를 고정한다</b>(2026-10-23 금 14:00 KST = 05:00Z) —
 * 호가 · 체결 시각의 날짜가 실행일에 따라 흔들리지 않게(메모리 pinned-clock).
 */
class NamuMarketQueryServiceImplTest {

    private static final long USER = 7L;
    private static final long OTHER = 8L;
    private static final String KR_PRICE = "/krstock/quote/v1/currentPrice";
    private static final String KR_ETF = "/krstock/quote/v1/etfCurrent";
    private static final String KR_PERIOD = "/krstock/quote/v1/period";
    private static final String GB_CURRENT = "/gbstock/quote/v1/current";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T05:00:00Z"), ZoneOffset.UTC);

    private NamuUpstreamFixture up;
    private NamuQueryService namuQueryService;
    private NamuMarketQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        up = new NamuUpstreamFixture(USER, OTHER);
        namuQueryService = org.mockito.Mockito.mock(NamuQueryService.class);
        org.mockito.BDDMockito.given(namuQueryService.resolveAccountNo(USER, null)).willReturn("33333333301");
        sut = new NamuMarketQueryServiceImpl(up.client, up.resolver, up.properties, namuQueryService, CLOCK);
    }

    /** 국내 현재가 응답 — 문서 칸 이름 그대로. 호가는 1 · 2단만, VI 칸은 Output_2. */
    private static final String KR_QUOTE_JSON = """
        {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.",
         "Output_0":{"iem_cd":"005930","iem_nm":"가람전자","stck_prpr":"70000","stck_prdy_clpr":"69500",
           "prdy_vrss_sign":"2","prdy_vrss":"500","stck_oprc":"69800","stck_hgpr":"70500","stck_lwpr":"69500",
           "acml_vol":"12345678","stck_mxpr":"90350","stck_llam":"48650",
           "hoga_bsop_hour":"09:30:15",
           "askp1":"70100","askp2":"70200","askp3":"0","askp_rsqn1":"1200","askp_rsqn2":"800",
           "bidp1":"70000","bidp2":"69900","bidp_rsqn1":"3000","bidp_rsqn2":"2500",
           "bstp_kor_isnm":"전기전자","hts_avls":"4521000","stck_lstn_date":"19750611",
           "lstn_stcn_unit0":"5969782550","per":"12.34","pbr":"1.20","w52_hgpr":"88800","w52_lwpr":"53000",
           "for_rate":"51.23","invt_epmd_yn":"Y","mrkt_alrm_code":"2","short_over_code":"3","sltr_yn":"N",
           "market_status":"1"},
         "Output_1":[{"bsop_hour":"09:30:15","stck_prpr":"70000","cntg_vol":"10"}],
         "Output_2":{"vi_type_code":"1"}}
        """;

    private static final String KR_PERIOD_JSON = """
        {"rsp_cd":"00000","rsp_msg":"ok","Output_0":{"iem_cd":"005930","eps":"5600"},
         "Output_1":[{"bsop_date":"20261023","stck_prpr":"70000"}]}
        """;

    @Nested
    @DisplayName("국내 상세 — currentPrice(UNT) 한 번 + EPS")
    class KrDetail {

        @Test
        @DisplayName("문서 칸이 우리 이름으로 옮겨진다 — 시총 억→원 · 호가 0단 제외 · 경고 이름표 · 장 상태")
        void mapsEveryField() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE)
                .andExpect(jsonPath("$.Input_0.market_cd").value("UNT"))
                .andExpect(jsonPath("$.Input_0.iem_cd").value("005930")), KR_QUOTE_JSON);
            respond(up.expect(KR_PERIOD)
                .andExpect(jsonPath("$.Input_0.gubun").value("1"))
                .andExpect(jsonPath("$.Input_0.array_cnt").value("1"))
                .andExpect(jsonPath("$.Input_0.edate").value("20261023")), KR_PERIOD_JSON);

            NamuQuoteDto.Detail d = sut.getKrQuote(USER, "005930");

            assertThat(d.symbol()).isEqualTo("005930");
            assertThat(d.name()).isEqualTo("가람전자");
            assertThat(d.currency()).isEqualTo("KRW");
            assertThat(d.price()).isEqualTo("70000");
            assertThat(d.previousClose()).isEqualTo("69500");
            assertThat(d.open()).isEqualTo("69800");
            assertThat(d.high()).isEqualTo("70500");
            assertThat(d.low()).isEqualTo("69500");
            assertThat(d.volume()).isEqualTo("12345678");
            assertThat(d.upperLimit()).isEqualTo("90350");
            assertThat(d.lowerLimit()).isEqualTo("48650");
            assertThat(d.marketState()).isEqualTo("REGULAR");
            assertThat(d.sector()).isEqualTo("전기전자");
            // hts_avls 4,521,000 억원 → 원.
            assertThat(d.marketCap()).isEqualTo("452100000000000");
            assertThat(d.listingDate()).isEqualTo("19750611");
            assertThat(d.listedShares()).isEqualTo("5969782550");
            assertThat(d.per()).isEqualTo("12.34");
            assertThat(d.pbr()).isEqualTo("1.20");
            assertThat(d.eps()).isEqualTo("5600");
            assertThat(d.high52w()).isEqualTo("88800");
            assertThat(d.low52w()).isEqualTo("53000");
            assertThat(d.foreignRate()).isEqualTo("51.23");
            assertThat(d.extendedSession()).isNull();
            assertThat(d.etf()).isNull();
            // 호가: 3단 가격이 0 이면 그 단은 건너뛴다. 토스 OrderbookResponse 와 같은 이름.
            TossMarketDto.OrderbookResponse ob = d.orderbook();
            assertThat(ob.currency()).isEqualTo("KRW");
            assertThat(ob.timestamp()).isEqualTo("2026-10-23T09:30:15+09:00");
            assertThat(ob.asks()).extracting(TossMarketDto.OrderbookEntry::price).containsExactly("70100", "70200");
            assertThat(ob.asks()).extracting(TossMarketDto.OrderbookEntry::volume).containsExactly("1200", "800");
            assertThat(ob.bids()).extracting(TossMarketDto.OrderbookEntry::price).containsExactly("70000", "69900");
            // 투자유의 Y · 투자경고(2) · 단기과열 지정(3) · VI 발동(1) — 정리매매 N 은 없다.
            assertThat(d.warnings()).extracting(TossStockDto.StockWarning::warningType)
                .containsExactly("INVESTMENT_ATTENTION", "INVESTMENT_WARNING", "SHORT_TERM_OVERHEAT", "VI");
            up.server.verify();
        }

        @Test
        @DisplayName("칸이 절반 빠진 응답도 null 로 내려간다 — 깨지지 않는다 (문서대로 구현, 나중에 고친다)")
        void survivesMissingFields() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), """
                {"Output_0":{"iem_cd":"005930","stck_prpr":"70000","stck_oprc":"0","hts_avls":""},
                 "message":{"msg_code":"MSG1","usr_msg":"정상"}}
                """);
            // EPS 호출이 실패해도 상세 전체를 실패시키지 않는다.
            up.expect(KR_PERIOD).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            NamuQuoteDto.Detail d = sut.getKrQuote(USER, "005930");

            assertThat(d.price()).isEqualTo("70000");
            assertThat(d.open()).as("0 은 값이 아니라 없다는 뜻").isNull();
            assertThat(d.name()).isNull();
            assertThat(d.previousClose()).isNull();
            assertThat(d.marketCap()).isNull();
            assertThat(d.eps()).isNull();
            assertThat(d.orderbook()).as("호가 단이 하나도 없으면 구역 자체를 안 그린다").isNull();
            assertThat(d.warnings()).isEmpty();
            assertThat(d.marketState()).isNull();
        }

        @Test
        @DisplayName("전일 종가 칸이 비면 전일대비(부호 코드)로 역산한다 — 지금 규칙 그대로")
        void previousCloseFallsBackToChange() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), """
                {"Output_0":{"stck_prpr":"70000","prdy_vrss":"500","prdy_vrss_sign":"2"}}
                """);
            respond(up.expect(KR_PERIOD), KR_PERIOD_JSON);

            assertThat(sut.getKrQuote(USER, "005930").previousClose()).isEqualTo("69500");
        }

        @Test
        @DisplayName("같은 사용자 · 같은 종목의 두 번째는 20초 캐시로 답한다 — 상류는 한 번만")
        void secondCallHitsCache() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), KR_QUOTE_JSON);
            respond(up.expect(KR_PERIOD), KR_PERIOD_JSON);

            NamuQuoteDto.Detail first = sut.getKrQuote(USER, "005930");
            NamuQuoteDto.Detail second = sut.getKrQuote(USER, "005930");

            assertThat(second).isSameAs(first);
            up.server.verify();
        }

        @Test
        @DisplayName("캐시 키에 사용자가 들어간다 — 같은 종목을 다른 사용자가 물으면 상류를 다시 부른다")
        void cacheIsPerUser() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            up.server.expect(ExpectedCount.times(2), requestTo(NamuUpstreamFixture.BASE + KR_PRICE))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer tok-")))
                .andRespond(withSuccess(KR_QUOTE_JSON, org.springframework.http.MediaType.APPLICATION_JSON));
            up.server.expect(ExpectedCount.times(2), requestTo(NamuUpstreamFixture.BASE + KR_PERIOD))
                .andRespond(withSuccess(KR_PERIOD_JSON, org.springframework.http.MediaType.APPLICATION_JSON));

            sut.getKrQuote(USER, "005930");
            sut.getKrQuote(OTHER, "005930");

            up.server.verify();
        }

        @Test
        @DisplayName("EPS 는 6시간 캐시 — 상세 캐시가 지나도 기간별 시세는 다시 부르지 않는다")
        void epsIsCachedLongerThanDetail() {
            up.properties.setQuoteCacheTtlSeconds(0); // 상세 캐시를 끈다
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            up.server.expect(ExpectedCount.times(2), requestTo(NamuUpstreamFixture.BASE + KR_PRICE))
                .andRespond(withSuccess(KR_QUOTE_JSON, org.springframework.http.MediaType.APPLICATION_JSON));
            respond(up.expect(KR_PERIOD), KR_PERIOD_JSON); // 한 번만

            assertThat(sut.getKrQuote(USER, "005930").eps()).isEqualTo("5600");
            assertThat(sut.getKrQuote(USER, "005930").eps()).isEqualTo("5600");

            up.server.verify();
        }

        @Test
        @DisplayName("period 의 Output_0 이 배열로 와도 첫 원소에서 eps 를 꺼낸다 — 문서가 객체 · 배열을 못 정한 자리")
        void epsFromArrayShape() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), KR_QUOTE_JSON);
            respond(up.expect(KR_PERIOD), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[{"iem_cd":"005930","eps":"4321"}]}
                """);

            assertThat(sut.getKrQuote(USER, "005930").eps()).isEqualTo("4321");
        }

        @Test
        @DisplayName("EPS 호출이 429 여도 상세는 EPS 없이 내려간다")
        void epsRateLimited() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), KR_QUOTE_JSON);
            up.expect(KR_PERIOD).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            assertThat(sut.getKrQuote(USER, "005930").eps()).isNull();
        }
    }

    @Nested
    @DisplayName("ETF — etfCurrent 한 번만 (현재가 · 기간별 시세를 부르지 않는다)")
    class EtfDetail {

        @Test
        @DisplayName("ETF 정보와 호가가 한 응답에서 온다")
        void etfCurrentOnly() {
            up.givenMaster("069500", StockMarket.KOSPI, StockSecurityType.ETF, "KRW");
            respond(up.expect(KR_ETF).andExpect(jsonPath("$.Input_0.iem_cd").value("069500")), """
                {"rsp_cd":"00000","rsp_msg":"ok",
                 "Output_0":{"iem_cd":"069500","iem_nm":"가람 국내 반도체 소부장 액티브","stck_prpr":"35000",
                   "prdy_clpr":"34800","stck_oprc":"34900","stck_hgpr":"35100","stck_lwpr":"34700",
                   "acml_vol":"2345678","stck_mxpr":"45240","stck_llam":"24360",
                   "askp1":"35010","askp_rsqn1":"500","bidp1":"35000","bidp_rsqn1":"700",
                   "bsop_hour":"09:31:00","bstp_kor_isnm":"ETF","hts_avls":"1200","lstn_date":"20020114",
                   "lstn_stcn":"34000000","w52_hgpr":"36000","w52_lwpr":"28000","for_rate":"0.00"},
                 "Output_3":{"itmt_last_nav":"34987.45","nav_vrss":"150.20","nav_sign":"2","dprt":"0.04",
                   "dprt_sign":"2","trc_errt":"0.12","totvalue":"11800","cnfg_cnt":"102"}}
                """);

            NamuQuoteDto.Detail d = sut.getKrQuote(USER, "069500");

            assertThat(d.price()).isEqualTo("35000");
            assertThat(d.previousClose()).isEqualTo("34800");
            assertThat(d.orderbook().asks()).hasSize(1);
            assertThat(d.marketState()).as("etfCurrent 엔 장 상태가 없다").isNull();
            assertThat(d.warnings()).isEmpty();
            // 투자 지표 줄은 for_rate 만 — PER · PBR · EPS · 52주는 null.
            assertThat(d.per()).isNull();
            assertThat(d.eps()).isNull();
            assertThat(d.high52w()).isNull();
            assertThat(d.foreignRate()).isEqualTo("0.00");
            assertThat(d.marketCap()).isEqualTo("120000000000");
            assertThat(d.listedShares()).isEqualTo("34000000");
            assertThat(d.etf()).isEqualTo(new NamuQuoteDto.Etf("34987.45", "150.20", "2", "0.04", "2",
                "0.12", "11800", "102"));
            up.server.verify(); // currentPrice · period 는 부르지 않았다
        }

        @Test
        @DisplayName("NAV 블록이 없으면 etf 만 null 이다")
        void navBlockMissing() {
            up.givenMaster("069500", StockMarket.KOSPI, StockSecurityType.ETF, "KRW");
            respond(up.expect(KR_ETF), "{\"Output_0\":{\"iem_cd\":\"069500\",\"stck_prpr\":\"35000\"}}");

            NamuQuoteDto.Detail d = sut.getKrQuote(USER, "069500");

            assertThat(d.price()).isEqualTo("35000");
            assertThat(d.etf()).isNull();
        }
    }

    @Nested
    @DisplayName("미국 상세 — current 한 번")
    class GbDetail {

        private static final String GB_JSON = """
            {"rsp_cd":"00000","rsp_msg":"ok",
             "Output_0":{"iem_cd":"MOA","iem_nm":"모아 테크놀로지스","currency_unit":"USD","trdprc":"230.10",
               "base_prc":"228.30","netchng":"1.80","netchng_cls":"2","open_prc":"228.50","high":"231.00",
               "low":"227.90","acvol":"45000000","uplimit":"0","lolimit":"0","quote_time":"093000",
               "best_ask1":"230.20","best_asiz1":"100","best_bid1":"230.10","best_bsiz1":"200",
               "industry_name":"반도체","list_amt":"3500000000000","list_num":"15000000000",
               "per_prc":"28.5","eps_prc":"8.07","w52high_prc":"260.10","w52low_prc":"164.08",
               "marketperiod_cls":"%s","pf_trdprc":"%s","pf_pctchng":"0.45","pf_netchng_cls":"2"}}
            """;

        @Test
        @DisplayName("애프터마켓이면 extendedSession 이 AFTER · 상하한가 0 은 null · 호가 시각은 뉴욕 시간")
        void afterMarket() {
            respond(up.expect(GB_CURRENT).andExpect(jsonPath("$.Input_0.iem_cd").value("MOA")),
                GB_JSON.formatted("2", "231.00"));

            NamuQuoteDto.Detail d = sut.getGbQuote(USER, "MOA");

            assertThat(d.currency()).isEqualTo("USD");
            assertThat(d.price()).isEqualTo("230.10");
            assertThat(d.previousClose()).isEqualTo("228.30");
            assertThat(d.upperLimit()).isNull();
            assertThat(d.lowerLimit()).isNull();
            assertThat(d.marketState()).isEqualTo("AFTER");
            assertThat(d.extendedSession()).isEqualTo(new NamuQuoteDto.ExtendedSession("AFTER", "231.00", "0.45", "2"));
            assertThat(d.warnings()).isEmpty();
            assertThat(d.sector()).isEqualTo("반도체");
            assertThat(d.marketCap()).isEqualTo("3500000000000");
            assertThat(d.listedShares()).isEqualTo("15000000000");
            assertThat(d.per()).isEqualTo("28.5");
            assertThat(d.eps()).isEqualTo("8.07");
            assertThat(d.pbr()).isNull();
            assertThat(d.high52w()).isEqualTo("260.10");
            assertThat(d.orderbook().timestamp()).isEqualTo("2026-10-23T09:30:00-04:00");
            assertThat(d.orderbook().bids()).extracting(TossMarketDto.OrderbookEntry::price).containsExactly("230.10");
        }

        @Test
        @DisplayName("정규장이면 시간외 줄이 없다 · 프리(0) · 주간거래(3) 는 각자 이름")
        void sessionNames() {
            respond(up.expect(GB_CURRENT), GB_JSON.formatted("1", "231.00"));
            respond(up.expect(GB_CURRENT), GB_JSON.formatted("0", "231.00"));
            assertThat(sut.getGbQuote(USER, "MOA").extendedSession()).isNull();

            assertThat(sut.getGbQuote(OTHER, "MOA").extendedSession().session()).isEqualTo("PRE");

            // 시간외 가격이 0 이면 3(주간거래)이어도 줄을 안 그린다 — 다른 사용자 키로 캐시를 피한다.
            NamuUpstreamFixture other = new NamuUpstreamFixture(USER);
            NamuMarketQueryServiceImpl impl = new NamuMarketQueryServiceImpl(other.client, other.resolver,
                other.properties, namuQueryService, CLOCK);
            other.expect(GB_CURRENT).andRespond(withSuccess(GB_JSON.formatted("3", "0"),
                org.springframework.http.MediaType.APPLICATION_JSON));
            assertThat(impl.getGbQuote(USER, "MOA").extendedSession()).isNull();
        }

        @Test
        @DisplayName("칸이 빠진 응답 — 통화는 USD 기본, 나머지는 null")
        void missingFields() {
            respond(up.expect(GB_CURRENT), "{\"Output_0\":{\"iem_cd\":\"MOA\",\"trdprc\":\"230.10\"}}");

            NamuQuoteDto.Detail d = sut.getGbQuote(USER, "MOA");

            assertThat(d.currency()).isEqualTo("USD");
            assertThat(d.price()).isEqualTo("230.10");
            assertThat(d.name()).isNull();
            assertThat(d.orderbook()).isNull();
            assertThat(d.marketState()).isNull();
        }
    }

    @Nested
    @DisplayName("코드 표")
    class CodeTables {

        @Test
        @DisplayName("국내 장 상태 — 0·1·2·A·C·D·E·F·X 와 모르는 값")
        void krMarketStates() {
            assertThat(NamuMarketCodes.krMarketState("0")).isEqualTo("PRE");
            assertThat(NamuMarketCodes.krMarketState("1")).isEqualTo("REGULAR");
            assertThat(NamuMarketCodes.krMarketState("2")).isEqualTo("AFTER");
            assertThat(NamuMarketCodes.krMarketState("A")).isEqualTo("SINGLE_PRICE");
            assertThat(NamuMarketCodes.krMarketState("C")).isEqualTo("HALTED");
            assertThat(NamuMarketCodes.krMarketState("D")).isEqualTo("BEFORE_OPEN");
            assertThat(NamuMarketCodes.krMarketState("E")).isEqualTo("CLOSED");
            assertThat(NamuMarketCodes.krMarketState("F")).isEqualTo("CLOSED");
            assertThat(NamuMarketCodes.krMarketState("X")).isEqualTo("CLOSED");
            assertThat(NamuMarketCodes.krMarketState("Z")).isNull();
            assertThat(NamuMarketCodes.krMarketState(null)).isNull();
        }

        @Test
        @DisplayName("미국 장 상태 — 0·1·2·3 과 모르는 값")
        void gbMarketStates() {
            assertThat(NamuMarketCodes.gbMarketState("0")).isEqualTo("PRE");
            assertThat(NamuMarketCodes.gbMarketState("1")).isEqualTo("REGULAR");
            assertThat(NamuMarketCodes.gbMarketState("2")).isEqualTo("AFTER");
            assertThat(NamuMarketCodes.gbMarketState("3")).isEqualTo("DAYTIME");
            assertThat(NamuMarketCodes.gbMarketState("9")).isNull();
            assertThat(NamuMarketCodes.gbMarketState(null)).isNull();
        }

        @Test
        @DisplayName("경고 이름표 표 — 투자주의경고 1~5 · 단기과열 1(예고)은 안 그림 · 정리매매 · VI 2 · 모르는 값은 버림")
        void warningTable() {
            assertThat(types(Map.of("mrkt_alrm_code", "1"), null)).containsExactly("INVESTMENT_CAUTION");
            assertThat(types(Map.of("mrkt_alrm_code", "3"), null)).containsExactly("INVESTMENT_CAUTION");
            assertThat(types(Map.of("mrkt_alrm_code", "2"), null)).containsExactly("INVESTMENT_WARNING");
            assertThat(types(Map.of("mrkt_alrm_code", "4"), null)).containsExactly("INVESTMENT_WARNING");
            assertThat(types(Map.of("mrkt_alrm_code", "5"), null)).containsExactly("INVESTMENT_RISK");
            assertThat(types(Map.of("mrkt_alrm_code", "9"), null)).isEmpty();
            assertThat(types(Map.of("short_over_code", "1"), null)).isEmpty();
            assertThat(types(Map.of("short_over_code", "2"), null)).containsExactly("SHORT_TERM_OVERHEAT");
            assertThat(types(Map.of("short_over_code", "3"), null)).containsExactly("SHORT_TERM_OVERHEAT");
            assertThat(types(Map.of("sltr_yn", "Y"), null)).containsExactly("LIQUIDATION_TRADING");
            assertThat(types(Map.of("sltr_yn", "N", "invt_epmd_yn", "N"), null)).isEmpty();
            assertThat(types(Map.of("invt_epmd_yn", "Y"), null)).containsExactly("INVESTMENT_ATTENTION");
            assertThat(types(Map.of(), Map.of("vi_type_code", "2"))).containsExactly("VI");
            assertThat(types(Map.of(), Map.of("vi_type_code", "0"))).isEmpty();
            assertThat(types(null, null)).isEmpty();
        }

        private List<String> types(Map<String, String> quote, Map<String, String> vi) {
            return NamuMarketCodes.krWarnings(quote, vi).stream().map(TossStockDto.StockWarning::warningType).toList();
        }
    }

    @Nested
    @DisplayName("체결 · 투자자별 · ETF 구성 · 시간외 · 증거금")
    class Others {

        @Test
        @DisplayName("국내 체결 — 토스 Trade 와 같은 이름 · 최신이 먼저 · count 상한 50")
        void krExecutions() {
            respond(up.expect("/krstock/quote/v1/currentExecution")
                .andExpect(jsonPath("$.Input_0.market_cd").value("UNT"))
                .andExpect(jsonPath("$.Input_0.array_cnt").value("50"))
                .andExpect(jsonPath("$.Input_0.view_main_yn").value("N")), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[
                  {"bsop_hour":"09:29:58","stck_prpr":"69900","cntg_vol":"5"},
                  {"bsop_hour":"09:30:01","stck_prpr":"70000","cntg_vol":"12"}]}
                """);

            List<TossMarketDto.Trade> trades = sut.getKrExecutions(USER, "005930", 100);

            assertThat(trades).extracting(TossMarketDto.Trade::price).containsExactly("70000", "69900");
            assertThat(trades.get(0).volume()).isEqualTo("12");
            assertThat(trades.get(0).timestamp()).isEqualTo("2026-10-23T09:30:01+09:00");
            assertThat(trades.get(0).currency()).isEqualTo("KRW");
        }

        @Test
        @DisplayName("국내 체결 — 기본 20건 · 칸이 빠진 줄은 null · 응답이 비면 빈 목록")
        void krExecutionsDefaults() {
            respond(up.expect("/krstock/quote/v1/currentExecution")
                .andExpect(jsonPath("$.Input_0.array_cnt").value("20")), """
                {"Output_0":[{"stck_prpr":"70000"}]}
                """);
            respond(up.expect("/krstock/quote/v1/currentExecution"),
                "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"조회할 내역이 없습니다.\"}");
            List<TossMarketDto.Trade> trades = sut.getKrExecutions(USER, "005930", null);
            assertThat(trades).hasSize(1);
            assertThat(trades.get(0).timestamp()).isNull();
            assertThat(trades.get(0).volume()).isNull();

            assertThat(sut.getKrExecutions(USER, "005930", 20)).isEmpty();
        }

        @Test
        @DisplayName("미국 체결 — executionTrend · req_cnt 는 숫자 · 시각은 거래소 현지(뉴욕, 서머타임 -04:00)")
        void gbExecutions() {
            up.givenMaster("MOA", StockMarket.NAS, StockSecurityType.STOCK, "USD");
            respond(up.expect("/gbstock/quote/v1/executionTrend")
                .andExpect(jsonPath("$.Input_0.period_type").value("1"))
                .andExpect(jsonPath("$.Input_0.req_cnt").value(20))
                .andExpect(jsonPath("$.Input_0.iem_cd").value("MOA")), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[
                  {"iem_cd":"MOA","trade_date":"20261022","trade_time":"155958","trdprc":"229.90","fill_size":"30"},
                  {"iem_cd":"MOA","trade_date":"20261022","trade_time":"160000","trdprc":"230.00","fill_size":"100"}]}
                """);

            List<TossMarketDto.Trade> trades = sut.getGbExecutions(USER, "MOA", null);

            assertThat(trades).extracting(TossMarketDto.Trade::price).containsExactly("230.00", "229.90");
            assertThat(trades.get(0).timestamp()).isEqualTo("2026-10-22T16:00:00-04:00");
            assertThat(trades.get(0).currency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("국내 투자자별 — 같은 모양 InvestorTradingDay · 날짜 yyyy-MM-dd · 최신 먼저")
        void krInvestors() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect("/krstock/quote/v1/currentInvestor")
                .andExpect(jsonPath("$.Input_0.market_cd").value("KRX"))
                .andExpect(jsonPath("$.Input_0.array_cnt").value("5")), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[
                  {"bsop_date1":"20261021","person":"-1200","frgn_ntby_qty":"3400","gigwan":"-2200","invest":"9"},
                  {"bsop_date1":"20261022","person":"500","frgn_ntby_qty":"-100","gigwan":"-400"},
                  {"bsop_date1":"","person":"1"}]}
                """);

            List<InvestorTradingDay> days = sut.getKrInvestors(USER, "005930", null);

            assertThat(days).containsExactly(
                new InvestorTradingDay("2026-10-22", "500", "-100", "-400"),
                new InvestorTradingDay("2026-10-21", "-1200", "3400", "-2200"));
        }

        @Test
        @DisplayName("미국 종목은 400 이 아니라 빈 목록 — 상류를 부르지 않는다")
        void usInvestorsEmpty() {
            up.givenMaster("MOA", StockMarket.NAS, StockSecurityType.STOCK, "USD");

            assertThat(sut.getKrInvestors(USER, "MOA", 5)).isEmpty();
            up.server.verify();
        }

        @Test
        @DisplayName("ETF 구성 종목 — 비중 큰 순으로 정렬해 내린다")
        void etfComponentsSortedByWeight() {
            respond(up.expect("/krstock/quote/v1/etfComponents")
                .andExpect(jsonPath("$.Input_0.iem_cd").value("069500")), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[
                  {"iem_cd":"A","iem_nm":"누리장비","stck_prpr":"1000","prdy_vrss_sign":"5","prdy_ctrt":"-1.2","vol":"3.10"},
                  {"iem_cd":"B","iem_nm":"가람전자","stck_prpr":"70000","prdy_vrss_sign":"2","prdy_ctrt":"0.7","vol":"25.40"},
                  {"iem_cd":"C","iem_nm":"비중 없음","stck_prpr":"5"}]}
                """);

            List<NamuQuoteDto.EtfComponent> list = sut.getEtfComponents(USER, "069500");

            assertThat(list).extracting(NamuQuoteDto.EtfComponent::symbol).containsExactly("B", "A", "C");
            assertThat(list.get(0)).isEqualTo(new NamuQuoteDto.EtfComponent("B", "가람전자", "25.40", "70000", "0.7", "2"));
            assertThat(list.get(2).weight()).isNull();
        }

        @Test
        @DisplayName("국내 시간외 — 체결가를 싣고 두 번째는 캐시 · 체결가가 0 이면 모든 칸 null")
        void krAfterHours() {
            respond(up.expect("/krstock/quote/v1/afterHoursCurrent"), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":{"iem_cd":"005930","ovtm_untp_prpr":"70100",
                 "prdy_vrss_sign":"2","ovtm_prdy_vrss":"100","ovtm_prdy_ctrt":"0.14","ovtm_cntg_hour":"16:05:00"}}
                """);

            respond(up.expect("/krstock/quote/v1/afterHoursCurrent"),
                "{\"Output_0\":{\"iem_cd\":\"000660\",\"ovtm_untp_prpr\":\"0\"}}");
            NamuQuoteDto.AfterHours first = sut.getKrAfterHours(USER, "005930");
            assertThat(first).isEqualTo(new NamuQuoteDto.AfterHours("70100", "100", "0.14", "2", "16:05:00"));
            assertThat(sut.getKrAfterHours(USER, "005930")).isSameAs(first);

            assertThat(sut.getKrAfterHours(USER, "000660")).isEqualTo(NamuQuoteDto.AfterHours.empty());
            up.server.verify();
        }

        @Test
        @DisplayName("해외 증거금 — USD 행의 예수금(fc_dca)을 싣는다 · 주문 가능 칸은 응답에 없다")
        void margin() {
            respond(up.expect("/gbstock/inquiry/v1/margin")
                .andExpect(jsonPath("$.Input_0.act_no").value("33333333301")), """
                {"rsp_cd":"00166","rsp_msg":"조회 완료","Output_0":[
                  {"cur_cd":"KRW","dca":"1000","fc_dca":"0","fc_drn_pbl_amt":"0"},
                  {"cur_cd":"USD","fc_dca":"1234.50","fc_drn_pbl_amt":"1200.00","sby_bse_xcg_rt":"1383.500000"}]}
                """);

            assertThat(sut.getMargin(USER)).isEqualTo(
                new NamuQuoteDto.Margin("USD", "1234.50", "1200.00", "1383.500000"));
        }

        @Test
        @DisplayName("해외 증거금 — USD 행이 없거나 계좌가 없으면 모든 칸 null")
        void marginEmpty() {
            respond(up.expect("/gbstock/inquiry/v1/margin"),
                "{\"Output_0\":[{\"cur_cd\":\"KRW\",\"dca\":\"1\"}]}");
            assertThat(sut.getMargin(USER)).isEqualTo(NamuQuoteDto.Margin.empty());

            org.mockito.BDDMockito.given(namuQueryService.resolveAccountNo(OTHER, null)).willReturn(null);
            assertThat(sut.getMargin(OTHER)).isEqualTo(NamuQuoteDto.Margin.empty());
            up.server.verify();
        }
    }

    @Nested
    @DisplayName("13b-0 검수 — 숫자 JSON · 0건 · 빈 종목")
    class Review {

        @Test
        @DisplayName("문서가 integer · number 로 적은 칸이 숫자 JSON 으로 와도 문자열 칸에 받는다")
        void numericJsonFixture() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            respond(up.expect(KR_PRICE), """
                {"rsp_cd":"00000","rsp_msg":"ok",
                 "Output_0":{"iem_cd":"005930","stck_prpr":70000,"stck_prdy_clpr":69500,"prdy_vrss":500,
                   "prdy_ctrt":0.72,"stck_oprc":69800,"acml_vol":12345678,"askp1":70100,"askp_rsqn1":1200,
                   "bidp1":70000,"bidp_rsqn1":3000,"hts_avls":4521000,"per":12.34,"for_rate":51.23,
                   "w52_hgpr":88800},
                 "Output_2":{"vi_type_code":"0"}}
                """);
            respond(up.expect(KR_PERIOD), "{\"rsp_cd\":\"00000\",\"Output_0\":{\"eps\":5600}}");

            NamuQuoteDto.Detail d = sut.getKrQuote(USER, "005930");

            assertThat(d.price()).isEqualTo("70000");
            assertThat(d.previousClose()).isEqualTo("69500");
            assertThat(d.volume()).isEqualTo("12345678");
            assertThat(d.per()).isEqualTo("12.34");
            assertThat(d.foreignRate()).isEqualTo("51.23");
            assertThat(d.eps()).isEqualTo("5600");
            assertThat(d.marketCap()).isEqualTo("452100000000000");
            assertThat(d.orderbook().asks().get(0).volume()).isEqualTo("1200");
        }

        @Test
        @DisplayName("체결 · 투자자별 · 증거금 행도 숫자 JSON 을 받는다")
        void numericRows() {
            respond(up.expect("/krstock/quote/v1/currentExecution"),
                "{\"Output_0\":[{\"bsop_hour\":\"09:30:01\",\"stck_prpr\":70000,\"cntg_vol\":12}]}");
            respond(up.expect("/gbstock/inquiry/v1/margin"),
                "{\"Output_0\":[{\"cur_cd\":\"USD\",\"fc_dca\":1234.5,\"fc_drn_pbl_amt\":1200,\"sby_bse_xcg_rt\":1383.5}]}");
            assertThat(sut.getKrExecutions(USER, "005930", 20).get(0).volume()).isEqualTo("12");
            assertThat(sut.getMargin(USER).foreignDeposit()).isEqualTo("1234.5");
        }

        @Test
        @DisplayName("0건 — 성공 코드에 블록이 없으면 오류가 아니라 빈 값이다 (시간외 · 증거금 · 체결 · 투자자별 · ETF 구성)")
        void emptyResultsAreNotErrors() {
            up.givenMaster("005930", StockMarket.KOSPI, StockSecurityType.STOCK, "KRW");
            String none = "{\"rsp_cd\":\"00000\",\"rsp_msg\":\"\"}";
            for (String path : List.of("/krstock/quote/v1/afterHoursCurrent", "/gbstock/inquiry/v1/margin",
                    "/krstock/quote/v1/currentExecution", "/krstock/quote/v1/currentInvestor",
                    "/krstock/quote/v1/etfComponents")) {
                respond(up.expect(path), none);
            }
            assertThat(sut.getKrAfterHours(USER, "005930")).isEqualTo(NamuQuoteDto.AfterHours.empty());
            assertThat(sut.getMargin(USER)).isEqualTo(NamuQuoteDto.Margin.empty());
            assertThat(sut.getKrExecutions(USER, "005930", 20)).isEmpty();
            assertThat(sut.getKrInvestors(USER, "005930", 5)).isEmpty();
            assertThat(sut.getEtfComponents(USER, "069500")).isEmpty();
            up.server.verify();
        }

        @Test
        @DisplayName("빈 종목은 상류에 보내지 않고 400")
        void blankSymbolRejected() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.getKrQuote(USER, " "))
                .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.getKrAfterHours(USER, null))
                .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            up.server.verify();
        }
    }
}
