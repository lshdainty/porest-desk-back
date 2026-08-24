package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.stock.repository.StockMasterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @InjectMocks private NamuQueryServiceImpl sut;

    private void givenAccounts(NamuAccountDto.Account... accounts) {
        given(namuApiClient.post(eq(USER), eq("/n2/acctinfo"), any(), any()))
            .willReturn(List.of(accounts));
    }

    private void givenKrBalance(NamuAccountDto.KrBalanceSummary summary, NamuAccountDto.KrHolding... items) {
        given(namuApiClient.<NamuPagedEnvelope<NamuAccountDto.KrBalanceSummary, NamuAccountDto.KrHolding>>exchange(
                eq(USER), eq("/krstock/inquiry/v1/balance"), any(), any(ParameterizedTypeReference.class)))
            .willReturn(new NamuPagedEnvelope<>("00000", "ok",
                summary == null ? List.of() : List.of(summary), List.of(items)));
    }

    private void givenGbBalance(NamuAccountDto.GbBalanceSummary summary, NamuAccountDto.GbHolding... items) {
        given(namuApiClient.<NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding>>exchange(
                eq(USER), eq("/gbstock/inquiry/v1/balance"), any(), any(ParameterizedTypeReference.class)))
            .willReturn(new NamuPagedEnvelope<>("00000", "ok",
                summary == null ? List.of() : List.of(summary), List.of(items)));
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
                new NamuAccountDto.GbBalanceSummary("2000000", "1500", "300", "20.0", "1383.50"),
                new NamuAccountDto.GbHolding("AAPL", "", "APPLE INC", "5", "180", "185.7", "928.5", "50", "1284000"));

            NamuAccountDto.Holdings h = sut.getHoldings(USER, ACCT, "USD");

            assertThat(h.currency()).isEqualTo("USD");
            assertThat(h.items()).singleElement()
                .extracting(NamuAccountDto.HoldingItem::name).isEqualTo("APPLE INC");
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

            verify(namuApiClient, never()).post(anyLong(), eq("/n2/acctinfo"), any(), any());
        }
    }

    @Nested
    @DisplayName("환율")
    class Fx {

        @Test
        @DisplayName("해외 잔고의 당일매매기준환율을 쓴다 — 나무엔 환율 전용 조회가 없다")
        void fromOverseasBalance() {
            givenAccounts(new NamuAccountDto.Account(ACCT, "01"));
            givenGbBalance(new NamuAccountDto.GbBalanceSummary("0", "0", "0", "0", "1383.50"));

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
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
        @DisplayName("요약이 비면 null")
        void nullWhenNoSummary() {
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
        verify(namuApiClient).post(eq(USER), eq("/n2/acctinfo"), eq(Map.of()), any());
    }
}
