package com.porest.desk.namu.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
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
import com.porest.desk.securities.type.NamuEnvironment;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.StockMasterResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
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
    /** 실계좌(acct_type=01). 나무 계좌번호는 11자리다. */
    private static final String LIVE_ACCT = "33333333301";

    @Mock private NamuApiClient namuApiClient;
    @Mock private StockMasterRepository stockMasterRepository;
    private NamuQueryServiceImpl sut;

    // 설정은 목이 아니라 진짜를 쓴다 — 목이면 TTL·예산이 0 이라 캐시가 죽은 채로 통과한다.
    @BeforeEach
    void setUpService() {
        sut = serviceFor(NamuEnvironment.LIVE);
    }

    private NamuQueryServiceImpl serviceFor(NamuEnvironment environment) {
        NamuProperties properties = new NamuProperties();
        properties.setEnvironment(environment);
        return new NamuQueryServiceImpl(namuApiClient,
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
    @DisplayName("국내 시세 — 거래소 코드")
    class KrMarketCode {

        @BeforeEach
        void givenPrice() {
            given(namuApiClient.<NamuMarketDto.KrPrice>postObject(
                    eq(USER), eq("/krstock/quote/v1/currentPrice"), any(), any()))
                .willReturn(new NamuMarketDto.KrPrice("70000", "69500", "5", "500", "0.72"));
        }

        @Test
        @DisplayName("미지정이면 KRX 로 나간다")
        void defaultsToKrx() {
            sut.getKrPrice(USER, "005930", null);

            assertThat(capturedBody()).containsEntry("market_cd", "KRX");
        }

        @Test
        @DisplayName("소문자도 대문자로 맞춰 보낸다 — 나무는 KRX 만 안다")
        void normalizesCase() {
            sut.getKrPrice(USER, "005930", "nxt");

            assertThat(capturedBody()).containsEntry("market_cd", "NXT");
        }

        @Test
        @DisplayName("StockMarket 어휘(KOSPI)를 태우면 400 으로 거절한다 — 예전엔 그 종목만 조용히 비었다")
        void rejectsStockMarketVocabulary() {
            // 이름이 같아서(marketCode) 다음 사람이 실제로 헷갈리는 값이다. 나무는 모르는 값을
            // 받으면 빈 응답을 주고, 화면에는 예외 없이 '—' 만 남는다.
            assertThatThrownBy(() -> sut.getKrPrice(USER, "005930", "KOSPI"))
                .isInstanceOf(InvalidValueException.class);

            verify(namuApiClient, never()).postObject(anyLong(), any(), any(), any());
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> capturedBody() {
            ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
            verify(namuApiClient).postObject(eq(USER), eq("/krstock/quote/v1/currentPrice"),
                body.capture(), any());
            return body.getValue();
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
                .willReturn(new NamuMarketDto.GbPrice("185.70", "184.00", "2", "1.70", "0.92", "USD", "1383.50"));

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
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
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
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
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

    }

    /**
     * <b>이 버그가 난 자리다.</b> 예전 코드는 {@code accounts.get(0)} 으로 첫 계좌를 집었는데,
     * 나무는 계좌구분({@code acct_type})이 그 계좌를 쓸 수 있는 도메인을 정한다
     * (운영 01·02 / 모의투자 03). 실제 사용자 목록이 {@code 03·03·01·03} 순으로 와서
     * 운영 도메인에 모의투자 계좌가 나갔고 {@code rsp_cd=11165} 로 거절당했다.
     * 목록 순서는 우리가 정하는 게 아니므로 순서에 기대지 않는지를 그 실제 순서로 고정한다.
     */
    @Nested
    @DisplayName("계좌 선택 — 환경과 계좌구분")
    class AccountSelection {

        /** 사용자 실측 순서. 모의투자(03)가 앞에 셋, 운영(01)이 세 번째. */
        private void givenRealWorldAccounts() {
            givenAccounts(
                NamuAccountDto.Account.of("11111111103", "03"),
                NamuAccountDto.Account.of("22222222203", "03"),
                NamuAccountDto.Account.of(LIVE_ACCT, "01"),
                NamuAccountDto.Account.of("44444444403", "03"));
        }

        @Test
        @DisplayName("운영 환경 — 목록 앞이 모의투자(03)여도 운영 계좌(01)를 고른다")
        void liveSkipsMockAccountsEvenWhenTheyComeFirst() {
            givenRealWorldAccounts();
            givenKrBalance(null);

            assertThat(sut.getHoldings(USER, null, "KRW").accountNo()).isEqualTo(LIVE_ACCT);
            assertThat(capturedKrBalanceInput()).containsEntry("act_no", LIVE_ACCT);
        }

        @Test
        @DisplayName("모의투자 환경 — 같은 목록에서 모의투자 계좌(03)를 고른다")
        void mockPicksMockAccount() {
            givenRealWorldAccounts();
            givenKrBalance(null);

            assertThat(serviceFor(NamuEnvironment.MOCK).getHoldings(USER, null, "KRW").accountNo())
                .isEqualTo("11111111103");
        }

        @Test
        @DisplayName("운영 환경에 모의투자 계좌만 있으면 원인을 말한다 — 502 로 뭉뚱그리지 않는다")
        void liveWithOnlyMockAccountsFailsWithReason() {
            givenAccounts(NamuAccountDto.Account.of("11111111103", "03"),
                NamuAccountDto.Account.of("22222222203", "03"));

            assertThatThrownBy(() -> sut.getHoldings(USER, null, "KRW"))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SECURITIES_ACCOUNT_ENVIRONMENT_UNAVAILABLE);

            verify(namuApiClient, never()).exchange(anyLong(), eq("/krstock/inquiry/v1/balance"), any(), any());
        }

        @Test
        @DisplayName("모의투자 환경에 운영 계좌만 있어도 같은 이유로 막는다 — 반대 방향도 사고다")
        void mockWithOnlyLiveAccountsFails() {
            givenAccounts(NamuAccountDto.Account.of(LIVE_ACCT, "01"));

            assertThatThrownBy(() -> serviceFor(NamuEnvironment.MOCK).getHoldings(USER, null, "KRW"))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("다른 환경의 계좌를 넘기면 거절한다 — 그냥 태우면 업스트림이 계좌오류로 거절한다")
        void rejectsAccountFromAnotherEnvironment() {
            givenRealWorldAccounts();

            assertThatThrownBy(() -> sut.getHoldings(USER, "11111111103", "KRW"))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SECURITIES_ACCOUNT_ENVIRONMENT_MISMATCH);

            verify(namuApiClient, never()).exchange(anyLong(), eq("/krstock/inquiry/v1/balance"), any(), any());
        }

        @Test
        @DisplayName("내 계좌가 아닌 번호를 넘기면 거절한다")
        void rejectsUnknownAccount() {
            givenRealWorldAccounts();

            assertThatThrownBy(() -> sut.getHoldings(USER, "99999999999", "KRW"))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SECURITIES_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("같은 환경의 계좌를 넘기면 그대로 쓴다")
        void acceptsAccountOfCurrentEnvironment() {
            givenRealWorldAccounts();
            givenKrBalance(null);

            assertThat(sut.getHoldings(USER, LIVE_ACCT, "KRW").accountNo()).isEqualTo(LIVE_ACCT);
        }

        @Test
        @DisplayName("계좌 목록은 거르지 않고 전부 준다 — 쓸 수 있는지만 usable 로 표시한다")
        void accountListIsNotFiltered() {
            givenRealWorldAccounts();

            assertThat(sut.getAccounts(USER)).hasSize(4)
                .filteredOn(a -> Boolean.TRUE.equals(a.usable()))
                .extracting(NamuAccountDto.Account::accountNo)
                .containsExactly(LIVE_ACCT);

            assertThat(serviceFor(NamuEnvironment.MOCK).getAccounts(USER))
                .filteredOn(a -> Boolean.TRUE.equals(a.usable())).hasSize(3);
        }

        @Test
        @DisplayName("계좌번호는 로그에 전체가 남지 않는다 — 뒤 4자리만")
        void accountNumberNeverReachesLogsInFull() {
            ListAppender<ILoggingEvent> appender = attachAppender();
            try {
                givenRealWorldAccounts();
                givenKrBalance(null);
                sut.getHoldings(USER, null, "KRW");

                assertThatThrownBy(() -> serviceFor(NamuEnvironment.MOCK).getHoldings(USER, LIVE_ACCT, "KRW"))
                    .isInstanceOf(InvalidValueException.class);

                assertThat(appender.list).isNotEmpty();
                assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(m -> m.contains(LIVE_ACCT))
                    .noneMatch(m -> m.contains("11111111103"));
            } finally {
                detachAppender(appender);
            }
        }

        @Test
        @DisplayName("마스킹은 뒤 4자리만 남기고, 짧은 값은 통째로 가린다")
        void maskKeepsOnlyLastFour() {
            assertThat(NamuQueryServiceImpl.maskAccountNo("12345678901")).isEqualTo("****8901");
            assertThat(NamuQueryServiceImpl.maskAccountNo("123")).isEqualTo("****");
            assertThat(NamuQueryServiceImpl.maskAccountNo(null)).isEqualTo("(없음)");
            assertThat(NamuQueryServiceImpl.maskAccountNo("  ")).isEqualTo("(없음)");
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> capturedKrBalanceInput() {
            ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
            verify(namuApiClient).exchange(eq(USER), eq("/krstock/inquiry/v1/balance"),
                body.capture(), any(ParameterizedTypeReference.class));
            return body.getValue();
        }

        private ListAppender<ILoggingEvent> attachAppender() {
            Logger logger = (Logger) LoggerFactory.getLogger(NamuQueryServiceImpl.class);
            logger.setLevel(Level.DEBUG);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        private void detachAppender(ListAppender<ILoggingEvent> appender) {
            ((Logger) LoggerFactory.getLogger(NamuQueryServiceImpl.class)).detachAppender(appender);
        }
    }

    @Nested
    @DisplayName("환율")
    class Fx {

        /** 시세 폴백 스텁 — 해외 현재가의 {@code currency_prc} 가 2순위 환율이다. */
        private void givenGbQuote(String currencyUnit, String currencyPrc) {
            given(namuApiClient.<NamuMarketDto.GbPrice>postObject(
                    eq(USER), eq("/gbstock/quote/v1/current"), any(), any()))
                .willReturn(new NamuMarketDto.GbPrice("185.70", "184.00", "2", "1.70", "0.92",
                    currencyUnit, currencyPrc));
        }

        @Test
        @DisplayName("1순위 — 환율은 종목 행(Output_1)에서 읽는다. 계좌 요약엔 없다")
        void fromHoldingRow() {
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
            givenGbBalance(new NamuAccountDto.GbBalanceSummary("0", "0", "0", "0"),
                new NamuAccountDto.GbHolding("AAPL", "애플", "APPLE INC", "5", "180", "185.7",
                    "928.5", "28.5", "1284000", "USD", "1383.50"));

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));

            // 잔고가 줬으면 시세는 안 부른다 — 계좌 평가에 실제 적용된 값이 이긴다.
            then(namuApiClient).should(never())
                .postObject(eq(USER), eq("/gbstock/quote/v1/current"), any(), any());
        }

        @Test
        @DisplayName("2순위 — 같은 통화 보유가 없으면 시세의 currency_prc 로 넘어간다")
        void fallsBackWhenCurrencyNotHeld() {
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
            givenGbBalance(new NamuAccountDto.GbBalanceSummary("0", "0", "0", "0"),
                new NamuAccountDto.GbHolding("7203", "도요타", "TOYOTA", "10", "2000", "2100",
                    "21000", "1000", "200000", "JPY", "9.12"));
            givenGbQuote("USD", "1381.20");

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1381.20"));
        }

        @Test
        @DisplayName("2순위 — 계좌가 없어도 환율을 구한다. 시세는 계좌를 안 탄다")
        void fallsBackWhenNoAccount() {
            givenAccounts();
            givenGbQuote("USD", "1379.00");

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1379.00"));
        }

        @Test
        @DisplayName("2순위 — 잔고 조회가 실패해도 시세로 넘어간다")
        void fallsBackWhenBalanceFails() {
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
            willThrow(new IllegalStateException("boom"))
                .given(namuApiClient).exchange(eq(USER), eq("/gbstock/inquiry/v1/balance"), any(), any());
            givenGbQuote("USD", "1385.75");

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1385.75"));
        }

        @Test
        @DisplayName("2순위 — 보유 종목이 없어도 시세로 구한다")
        void fallsBackWhenNoHoldings() {
            givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));
            givenGbBalance(null);
            givenGbQuote("USD", "1380.10");

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1380.10"));
        }

        @Test
        @DisplayName("둘 다 못 구하면 null — 그때만 호출부가 외화 평가를 접는다")
        void nullWhenBothPathsFail() {
            givenAccounts();
            // 시세 응답 없음(목 기본값 null).

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }

        @Test
        @DisplayName("폴백 종목의 통화가 다르면 쓰지 않는다 — 조용히 틀린 환율이 화면에 나간다")
        void nullWhenProbeCurrencyDiffers() {
            givenAccounts();
            givenGbQuote("JPY", "891.50");

            assertThat(sut.getFxRate(USER, "USD")).isNull();
        }
    }

    @Test
    @DisplayName("계좌목록조회는 입력이 없어도 Input_0 봉투를 지킨다")
    void accountsKeepEnvelope() {
        givenAccounts(NamuAccountDto.Account.of(ACCT, "01"));

        assertThat(sut.getAccounts(USER)).hasSize(1);
        verify(namuApiClient).postList(eq(USER), eq("/n2/acctinfo"), eq(Map.of()), any());
    }
}
