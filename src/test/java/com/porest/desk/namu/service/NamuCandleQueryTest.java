package com.porest.desk.namu.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.config.NamuApiClientConfig;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.SecuritiesCandle;
import com.porest.desk.securities.type.CandleInterval;
import com.porest.desk.securities.type.SecuritiesBroker;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.StockMasterResolver;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 나무 캔들(기간별시세) — <b>실제 JSON 을 태워서</b> 본다.
 *
 * <p>목으로 자바 객체만 만들어 넣으면 Jackson 이 개입할 자리가 없어 <b>봉투 구조 오류를
 * 한 건도 못 잡는다.</b> 이 레포는 정확히 그래서 한 번 크게 당했다(#254) —
 * {@code Output_0} 을 배열로 선언했다가 나무 조회가 통째로 실패했다.
 *
 * <p>여기서 지키는 것:
 * <ul>
 *   <li>NH 스펙이 ⚠️ 를 단 자리 — {@code Output_0} 이 <b>객체로 와도 배열로 와도</b> 읽힌다</li>
 *   <li>국내와 해외의 <b>주기구분 숫자가 다르다</b>(국내 1=일 / 해외 3=일)</li>
 *   <li>봉 시각에 <b>거래소 오프셋</b>이 붙는다(국내 +09:00 / 미국 서머타임 -04:00)</li>
 *   <li>커서가 <b>가장 오래된 봉의 하루 전</b>이다 — 나무엔 불투명 커서가 없다</li>
 *   <li>같은 요청은 캐시로 답한다 — 나무는 캔들도 종목당 1콜이고 429 가 있다</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuCandleQueryTest {

    private static final String BASE = "https://api.nhplug.com:8443";
    private static final String KR_PERIOD = "/krstock/quote/v1/period";
    private static final String GB_PERIOD = "/gbstock/quote/v1/period";
    private static final long USER = 7L;

    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tokenManager;
    @Mock private StockMasterRepository stockMasterRepository;

    private MockRestServiceServer server;
    private NamuQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(tokenManager.authHeaders(USER)).willReturn(auth);

        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl(BASE);
        // 운영과 같은 RestTemplate 을 쓴다 — 컨버터 설정 차이로 테스트만 통과하는 일이 없게.
        RestTemplate restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.createServer(restTemplate);
        sut = new NamuQueryServiceImpl(new NamuApiClient(restTemplate, tokenManagers, properties),
            new StockMasterResolver(stockMasterRepository), properties);
    }

    private void givenMaster(String symbol, StockMarket market, String currency) {
        StockMaster stock = StockMaster.create(MasterSource.KIS, InstrumentRecord.kis(
            market, symbol, null, null, symbol, symbol, StockSecurityType.STOCK, currency));
        given(stockMasterRepository.findAllActiveBySymbol(symbol)).willReturn(List.of(stock));
    }

    private org.springframework.test.web.client.ResponseActions expect(String path) {
        return server.expect(requestTo(BASE + path)).andExpect(method(HttpMethod.POST));
    }

    private static void respond(org.springframework.test.web.client.ResponseActions actions, String json) {
        actions.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static CandleQuery daily(String symbol, int size, String cursor) {
        return new CandleQuery(symbol, CandleInterval.DAY_1, size, cursor, null);
    }

    /** 국내 일봉 2건. <b>최신이 먼저</b> 오도록 두어 우리가 오름차순으로 뒤집는지를 본다. */
    private static String krDailyJson(String output0) {
        return """
            {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.",
             "Output_0":%s,
             "Output_1":[
               {"bsop_date":"20260826","bsop_time":"","stck_oprc":"69000","stck_hgpr":"70500",
                "stck_lwpr":"68800","stck_prpr":"70000","vol":"12345678","tr_pbmn":"900"},
               {"bsop_date":"20260825","bsop_time":"","stck_oprc":"68500","stck_hgpr":"69200",
                "stck_lwpr":"68000","stck_prpr":"69000","vol":"9876543","tr_pbmn":"800"}]}
            """.formatted(output0);
    }

    @Nested
    @DisplayName("Output_0 은 객체로 와도 배열로 와도 읽힌다 — NH 가 ⚠️ 를 단 자리")
    class AmbiguousOutput0 {

        @Test
        @DisplayName("객체로 오는 경우 (스펙의 예시 응답)")
        void objectShape() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), krDailyJson(
                "{\"iem_cd\":\"005930\",\"iem_nm\":\"삼성전자\",\"stck_prpr\":\"70000\"}"));

            assertThat(sut.getCandles(USER, daily("005930", 2, null)).candles()).hasSize(2);
            server.verify();
        }

        @Test
        @DisplayName("배열로 오는 경우 (스펙의 선언 타입) — 같은 코드가 그대로 읽는다")
        void arrayShape() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), krDailyJson("[{\"iem_cd\":\"005930\",\"stck_prpr\":\"70000\"}]"));

            assertThat(sut.getCandles(USER, daily("005930", 2, null)).candles()).hasSize(2);
            server.verify();
        }

        @Test
        @DisplayName("아예 없는 경우 — 나무는 데이터가 있을 때만 블록을 내려준다")
        void missingShape() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), """
                {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.",
                 "Output_1":[{"bsop_date":"20260826","stck_oprc":"69000","stck_hgpr":"70500",
                              "stck_lwpr":"68800","stck_prpr":"70000","vol":"1"}]}
                """);

            assertThat(sut.getCandles(USER, daily("005930", 2, null)).candles()).hasSize(1);
            server.verify();
        }
    }

    @Nested
    @DisplayName("국내 일봉")
    class KrDaily {

        @Test
        @DisplayName("주기구분 1(일) · 종료일은 오늘 · 시각에 +09:00 이 붙고 오래된 봉이 먼저다")
        void mapsRows() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            String today = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneId.of("Asia/Seoul")));
            respond(expect(KR_PERIOD)
                    .andExpect(jsonPath("$.Input_0.market_cd").value("KRX"))
                    .andExpect(jsonPath("$.Input_0.iem_cd").value("005930"))
                    .andExpect(jsonPath("$.Input_0.gubun").value("1"))
                    .andExpect(jsonPath("$.Input_0.array_cnt").value("2"))
                    .andExpect(jsonPath("$.Input_0.today_cls_code").value("0"))
                    .andExpect(jsonPath("$.Input_0.edate").value(today))
                    // 일봉에는 xtick 을 넣지 않는다 — 스펙이 "분/초/틱시 입력" 이다.
                    .andExpect(jsonPath("$.Input_0.xtick").doesNotExist()),
                krDailyJson("{\"iem_cd\":\"005930\"}"));

            List<SecuritiesCandle> candles = sut.getCandles(USER, daily("005930", 2, null)).candles();

            assertThat(candles).extracting(SecuritiesCandle::timestamp)
                .containsExactly("2026-08-25T00:00:00+09:00", "2026-08-26T00:00:00+09:00");
            assertThat(candles.get(1))
                .extracting(SecuritiesCandle::openPrice, SecuritiesCandle::highPrice,
                    SecuritiesCandle::lowPrice, SecuritiesCandle::closePrice,
                    SecuritiesCandle::volume, SecuritiesCandle::currency)
                .containsExactly("69000", "70500", "68800", "70000", "12345678", "KRW");
            server.verify();
        }

        @Test
        @DisplayName("커서는 가장 오래된 봉의 하루 전 — 나무엔 불투명 커서가 없다")
        void cursorIsDayBeforeOldest() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), krDailyJson("{}"));

            assertThat(sut.getCandles(USER, daily("005930", 2, null)).nextCursor()).isEqualTo("20260824");
        }

        @Test
        @DisplayName("요청보다 적게 오면 끝이다 — 상장 이전까지 다 읽었다")
        void shortPageEndsPaging() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), krDailyJson("{}"));

            CandlePage page = sut.getCandles(USER, daily("005930", 50, null));
            assertThat(page.candles()).hasSize(2);
            assertThat(page.nextCursor()).isNull();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        @DisplayName("커서를 받으면 그 날짜를 종료일로 태운다")
        void cursorBecomesEndDate() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD).andExpect(jsonPath("$.Input_0.edate").value("20260824")),
                krDailyJson("{}"));

            sut.getCandles(USER, daily("005930", 2, "20260824"));
            server.verify();
        }

        @Test
        @DisplayName("나무 커서가 아니면 첫 페이지로 되돌린다 — 기본 증권사를 바꾸면 토스 커서가 넘어온다")
        void foreignCursorFallsBackToToday() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            String today = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneId.of("Asia/Seoul")));
            respond(expect(KR_PERIOD).andExpect(jsonPath("$.Input_0.edate").value(today)),
                krDailyJson("{}"));

            sut.getCandles(USER, daily("005930", 2, "eyJiZWZvcmUiOiIxNzI0In0="));
            server.verify();
        }
    }

    @Nested
    @DisplayName("국내 분봉")
    class KrMinute {

        @Test
        @DisplayName("주기구분 5(분) + xtick 001 을 태우고, 커서는 주지 않는다")
        void minuteHasNoCursor() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD)
                    .andExpect(jsonPath("$.Input_0.gubun").value("5"))
                    .andExpect(jsonPath("$.Input_0.xtick").value("001")), """
                {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.","Output_0":{},
                 "Output_1":[
                   {"bsop_date":"20260826","bsop_time":"090100","stck_oprc":"69000","stck_hgpr":"69100",
                    "stck_lwpr":"68900","stck_prpr":"69050","vol":"1200"},
                   {"bsop_date":"20260826","bsop_time":"090000","stck_oprc":"68900","stck_hgpr":"69000",
                    "stck_lwpr":"68800","stck_prpr":"68950","vol":"3400"}]}
                """);

            CandlePage page = sut.getCandles(USER,
                new CandleQuery("005930", CandleInterval.MINUTE_1, 2, null, null));

            assertThat(page.candles()).extracting(SecuritiesCandle::timestamp)
                .containsExactly("2026-08-26T09:00:00+09:00", "2026-08-26T09:01:00+09:00");
            // 종료일이 날짜 단위라 하루 안에서 더 과거로 갈 방법이 없다.
            assertThat(page.nextCursor()).isNull();
            server.verify();
        }
    }

    @Nested
    @DisplayName("해외 일봉")
    class GbDaily {

        @Test
        @DisplayName("주기구분 3(일) — 국내의 1 과 다르다. 필수 8개를 모두 태운다")
        void overseasUsesDifferentGubun() {
            givenMaster("AAPL", StockMarket.NAS, "USD");
            String today = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneId.of("America/New_York")));
            respond(expect(GB_PERIOD)
                    .andExpect(jsonPath("$.Input_0.iem_cd").value("AAPL"))
                    .andExpect(jsonPath("$.Input_0.end_dt").value(today))
                    .andExpect(jsonPath("$.Input_0.count").value("2"))
                    .andExpect(jsonPath("$.Input_0.maxavg").value("020"))
                    .andExpect(jsonPath("$.Input_0.gubun").value("3"))
                    .andExpect(jsonPath("$.Input_0.xtick").value("0001"))
                    .andExpect(jsonPath("$.Input_0.today_cls").value("0"))
                    .andExpect(jsonPath("$.Input_0.market_cls").value("1")), """
                {"rsp_cd":"00000","rsp_msg":"정상처리 되었습니다.",
                 "Output_0":[{"iem_cd":"AAPL","trdprc":"230.10"}],
                 "Output_1":[
                   {"trade_date":"20260826","trade_time":"","open_prc":"228.50","high":"231.00",
                    "low":"227.90","close_prc":"230.10","movolume":"45000000","bsop_date":"20260827"},
                   {"trade_date":"20260825","trade_time":"","open_prc":"226.00","high":"229.40",
                    "low":"225.10","close_prc":"228.30","movolume":"38000000","bsop_date":"20260826"}]}
                """);

            List<SecuritiesCandle> candles = sut.getCandles(USER, daily("AAPL", 2, null)).candles();

            // 미국 서머타임(EDT) 이라 -04:00. 오프셋을 빼면 받는 쪽이 자기 타임존으로 읽어 날짜가 밀린다.
            assertThat(candles).extracting(SecuritiesCandle::timestamp)
                .containsExactly("2026-08-25T00:00:00-04:00", "2026-08-26T00:00:00-04:00");
            assertThat(candles.get(1))
                .extracting(SecuritiesCandle::openPrice, SecuritiesCandle::closePrice,
                    SecuritiesCandle::volume, SecuritiesCandle::currency)
                .containsExactly("228.50", "230.10", "45000000", "USD");
            server.verify();
        }

        @Test
        @DisplayName("분봉은 주기구분 2 — 국내의 5 와 다르다")
        void overseasMinuteGubun() {
            givenMaster("AAPL", StockMarket.NAS, "USD");
            respond(expect(GB_PERIOD).andExpect(jsonPath("$.Input_0.gubun").value("2")),
                """
                {"rsp_cd":"00000","rsp_msg":"정상처리 되었습니다.","Output_0":[],
                 "Output_1":[{"trade_date":"20260826","trade_time":"093000","open_prc":"228.50",
                              "high":"229.00","low":"228.10","close_prc":"228.80","movolume":"120000"}]}
                """);

            List<SecuritiesCandle> candles = sut.getCandles(USER,
                new CandleQuery("AAPL", CandleInterval.MINUTE_1, 2, null, null)).candles();

            assertThat(candles).extracting(SecuritiesCandle::timestamp)
                .containsExactly("2026-08-26T09:30:00-04:00");
            server.verify();
        }
    }

    @Nested
    @DisplayName("망가진 응답을 견딘다")
    class Defensive {

        @Test
        @DisplayName("Output_1 이 없으면 빈 페이지 — 상장 직후·장 시작 전은 오류가 아니다")
        void emptyBlockIsNotAnError() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), """
                {"rsp_cd":"13578","rsp_msg":"조회할 내역이 없습니다.","Output_0":{}}
                """);

            CandlePage page = sut.getCandles(USER, daily("005930", 200, null));
            assertThat(page.candles()).isEmpty();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("시가·고가·저가가 비면 종가로 채운다 — NaN 이 가면 캔들이 통째로 안 그려진다")
        void blankOhlcFallsBackToClose() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":{},
                 "Output_1":[{"bsop_date":"20260826","stck_oprc":"","stck_hgpr":null,
                              "stck_lwpr":"","stck_prpr":"70000","vol":""}]}
                """);

            SecuritiesCandle bar = sut.getCandles(USER, daily("005930", 200, null)).candles().get(0);
            assertThat(bar.openPrice()).isEqualTo("70000");
            assertThat(bar.highPrice()).isEqualTo("70000");
            assertThat(bar.lowPrice()).isEqualTo("70000");
            assertThat(bar.volume()).isEqualTo("0");
        }

        @Test
        @DisplayName("날짜나 종가가 없는 봉만 버린다 — 한 줄 때문에 차트를 접지 않는다")
        void dropsOnlyBrokenRows() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD), """
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":{},
                 "Output_1":[{"bsop_date":"","stck_prpr":"70000"},
                             {"bsop_date":"20260826","stck_prpr":""},
                             {"bsop_date":"20260826","stck_oprc":"69000","stck_hgpr":"70500",
                              "stck_lwpr":"68800","stck_prpr":"70000","vol":"1"}]}
                """);

            assertThat(sut.getCandles(USER, daily("005930", 200, null)).candles())
                .extracting(SecuritiesCandle::closePrice).containsExactly("70000");
        }

        @Test
        @DisplayName("마스터에 없는 종목은 400 — 시세와 달리 빈 차트로 얼버무리지 않는다")
        void unknownSymbolIsRejected() {
            given(stockMasterRepository.findAllActiveBySymbol("ZZZZ")).willReturn(List.of());

            assertThatThrownBy(() -> sut.getCandles(USER, daily("ZZZZ", 200, null)))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SECURITIES_SYMBOL_INVALID);
        }
    }

    @Nested
    @DisplayName("유량 제한 — 캔들도 종목당 1콜이다")
    class Caching {

        @Test
        @DisplayName("같은 요청을 다시 하면 캐시로 답한다 — 기간 탭은 전부 같은 일봉 첫 페이지를 부른다")
        void secondCallHitsCache() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            // 기대는 한 번만 등록한다 — 두 번째 요청이 나가면 MockRestServiceServer 가 실패시킨다.
            respond(expect(KR_PERIOD), krDailyJson("{}"));

            assertThat(sut.getCandles(USER, daily("005930", 200, null)).candles()).hasSize(2);
            assertThat(sut.getCandles(USER, daily("005930", 200, null)).candles()).hasSize(2);
            server.verify();
        }

        @Test
        @DisplayName("주기가 다르면 캐시를 나눠 쓰지 않는다 — 분봉 자리에 일봉이 그려지면 안 된다")
        void cacheIsPerInterval() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            respond(expect(KR_PERIOD).andExpect(jsonPath("$.Input_0.gubun").value("1")), krDailyJson("{}"));
            respond(expect(KR_PERIOD).andExpect(jsonPath("$.Input_0.gubun").value("5")), krDailyJson("{}"));

            sut.getCandles(USER, daily("005930", 200, null));
            sut.getCandles(USER, new CandleQuery("005930", CandleInterval.MINUTE_1, 200, null, null));
            server.verify();
        }

        @Test
        @DisplayName("사용자가 다르면 캐시를 공유하지 않는다 — 남의 키로 받은 응답이다")
        void cacheIsPerUser() {
            givenMaster("005930", StockMarket.KOSPI, "KRW");
            HttpHeaders auth = new HttpHeaders();
            auth.setBearerAuth("tok2");
            given(tokenManager.authHeaders(8L)).willReturn(auth);
            respond(expect(KR_PERIOD), krDailyJson("{}"));
            respond(expect(KR_PERIOD), krDailyJson("{}"));

            sut.getCandles(USER, daily("005930", 200, null));
            sut.getCandles(8L, daily("005930", 200, null));
            server.verify();
        }
    }
}
