package io.groo.damda.namu.service;

import io.groo.core.exception.InvalidValueException;
import io.groo.damda.namu.dto.NamuAccountDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static io.groo.damda.namu.service.NamuUpstreamFixture.respond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 13b-0 — 나무 "계좌 자세히"(계좌 자산 · 실현손익 · 권리). 가짜 상류로 문서의 응답 모양을 태운다.
 * 시계는 2026-10-23 로 고정한다(지난 권리의 "올해 1월 1일" 이 실행일에 흔들리지 않게).
 */
class NamuAccountDetailQueryServiceImplTest {

    private static final long USER = 7L;
    private static final String ACCT = "33333333301";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T05:00:00Z"), ZoneOffset.UTC);

    private NamuUpstreamFixture up;
    private NamuQueryService namuQueryService;
    private NamuAccountDetailQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        up = new NamuUpstreamFixture(USER);
        namuQueryService = org.mockito.Mockito.mock(NamuQueryService.class);
        given(namuQueryService.resolveAccountNo(USER, null)).willReturn(ACCT);
        sut = new NamuAccountDetailQueryServiceImpl(up.client, namuQueryService, up.properties, CLOCK);
    }

    @Test
    @DisplayName("계좌 자산 — assetStatus 요청(시가평가 · 총자산 · UNT · 정규장)과 칸 옮기기")
    void assets() {
        respond(up.expect("/krstock/inquiry/v1/assetStatus")
            .andExpect(jsonPath("$.Input_0.act_no").value(ACCT))
            .andExpect(jsonPath("$.Input_0.eal_aly_cd").value("2"))
            .andExpect(jsonPath("$.Input_0.aet_bse").value("2"))
            .andExpect(jsonPath("$.Input_0.qut_dit_cd").value("UNT"))
            .andExpect(jsonPath("$.Input_0.aly_qut_cd").value("1")), """
            {"rsp_cd":"00166","rsp_msg":"정상적으로 조회되었습니다.",
             "Output_0":{"tot_aet_amt":"2034567890","nas_amt":"2000000000","dca":"1500000",
                         "nxt2_dd_dca":"1400000","drn_pbl_amt":"1300000","stk_orr_pbl_amt":"9"}}
            """);

        assertThat(sut.getAssetStatus(USER)).isEqualTo(new NamuAccountDetailDto.AssetStatus(
            "2034567890", "2000000000", "1500000", "1400000", "1300000"));
    }

    @Test
    @DisplayName("계좌 자산 — 계좌가 없거나 칸이 비면 null 칸")
    void assetsEmpty() {
        given(namuQueryService.resolveAccountNo(8L, null)).willReturn(null);
        assertThat(sut.getAssetStatus(8L)).isEqualTo(new NamuAccountDetailDto.AssetStatus(null, null, null, null, null));

        respond(up.expect("/krstock/inquiry/v1/assetStatus"), "{\"Output_0\":{\"dca\":\"1\",\"tot_aet_amt\":\"\"}}");
        NamuAccountDetailDto.AssetStatus a = sut.getAssetStatus(USER);
        assertThat(a.deposit()).isEqualTo("1");
        assertThat(a.totalAssets()).isNull();
    }

    @Test
    @DisplayName("실현손익 — 국내 종목별 + 미국 날짜별, 판 것만, 두 조회를 이 차례로 직렬")
    void realizedPnl() {
        respond(up.expect("/krstock/inquiry/v1/tradingPnl")
            .andExpect(jsonPath("$.Input_0.iqr_sta_dt").value("20260801"))
            .andExpect(jsonPath("$.Input_0.iqr_end_dt").value("20261023")), """
            {"rsp_cd":"00166","rsp_msg":"ok",
             "Output_0":{"pls_amt":"125000"},
             "Output_1":[{"iem_cd":"005930","iem_nm":"가람전자 아주 긴 종목 이름 우선주 테스트","sll_qty":"10","pls_amt":"150000","pft_rt":"12.5"},
                         {"iem_cd":"000660","iem_nm":"별빛반도체","sll_qty":"0","pls_amt":"0","pft_rt":"0"},
                         {"iem_cd":"035420","iem_nm":"숲길소재","sll_qty":"5","pls_amt":"-25000","pft_rt":"-3.1"}]}
            """);
        respond(up.expect("/gbstock/inquiry/v1/periodPnl")
            .andExpect(jsonPath("$.Input_0.iqr_dit").value("2"))
            .andExpect(jsonPath("$.Input_0.sta_orr_dt").value("20260801"))
            .andExpect(jsonPath("$.Input_0.end_orr_dt").value("20261023"))
            .andExpect(jsonPath("$.Input_0.iem_cd").value(""))
            .andExpect(jsonPath("$.Input_0.trd_cur_cd").value("USD"))
            .andExpect(jsonPath("$.Input_0.fc_sec_trd_nat_cd").value("200")), """
            {"rsp_cd":"00000","rsp_msg":"ok",
             "Output_0":{"fc_rzt_pls_sum":"1412370"},
             "Output_1":[{"orr_dt":"20261002","sll_qty":"15","fc_rzt_pls":"1412370","fc_rzt_pft_rt":"8.2"},
                         {"orr_dt":"20261001","sll_qty":"0","fc_rzt_pls":"0"}]}
            """);

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krTotal()).isEqualTo("125000");
        assertThat(r.krItems()).extracting(NamuAccountDetailDto.KrPnlItem::symbol).containsExactly("005930", "035420");
        assertThat(r.krItems().get(1)).isEqualTo(
            new NamuAccountDetailDto.KrPnlItem("035420", "숲길소재", "5", "-25000", "-3.1"));
        assertThat(r.usTotal()).isEqualTo("1412370");
        assertThat(r.usRows()).containsExactly(new NamuAccountDetailDto.UsPnlRow("2026-10-02", "15", "1412370", "8.2"));
        up.server.verify();
    }

    @Test
    @DisplayName("한쪽 조회가 실패하면 그쪽만 null — 다른 쪽은 남는다 (미국 실패)")
    void oneSideFails() {
        respond(up.expect("/krstock/inquiry/v1/tradingPnl"),
            "{\"Output_0\":{\"pls_amt\":\"1\"},\"Output_1\":[{\"iem_cd\":\"005930\",\"sll_qty\":\"1\",\"pls_amt\":\"1\"}]}");
        up.expect("/gbstock/inquiry/v1/periodPnl").andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krTotal()).isEqualTo("1");
        assertThat(r.krItems()).hasSize(1);
        assertThat(r.usTotal()).isNull();
        assertThat(r.usRows()).isNull();
    }

    @Test
    @DisplayName("한쪽 조회가 실패하면 그쪽만 null (국내 429 · 미국은 0건)")
    void krRateLimited() {
        up.expect("/krstock/inquiry/v1/tradingPnl").andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        respond(up.expect("/gbstock/inquiry/v1/periodPnl"),
            "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"조회할 내역이 없습니다.\"}");

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krTotal()).isNull();
        assertThat(r.krItems()).isNull();
        assertThat(r.usTotal()).isNull();
        assertThat(r.usRows()).as("0건은 실패가 아니라 빈 목록").isEmpty();
    }

    @Test
    @DisplayName("기간이 거꾸로면 400 · 계좌가 없으면 빈 손익")
    void rangeAndNoAccount() {
        assertThatThrownBy(() -> sut.getRealizedPnl(USER, LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 1)))
            .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> sut.getRealizedPnl(USER, null, LocalDate.of(2026, 10, 1)))
            .isInstanceOf(InvalidValueException.class);

        given(namuQueryService.resolveAccountNo(8L, null)).willReturn(null);
        assertThat(sut.getRealizedPnl(8L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 23)))
            .isEqualTo(new NamuAccountDetailDto.RealizedPnl(null, null, null, null, false));
    }

    @Test
    @DisplayName("권리 예정 — rightsScheduled(Output_0 배열) · 유형 이름 · 배정수량")
    void rightsScheduled() {
        respond(up.expect("/krstock/inquiry/v1/rightsScheduled")
            .andExpect(jsonPath("$.Input_0.act_no").value(ACCT)), """
            {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[
              {"iem_cd":"005930","iem_nm":"가람전자","rit_tp_cd":"01","rit_tp_nm":"배당","aloc_qty":"100","bse_dt":"20261230"},
              {"iem_cd":"000660","iem_nm":"별빛반도체","rit_tp_cd":"05","rit_tp_nm":"신주인수권증서","aloc_qty":"1000"}]}
            """);

        List<NamuAccountDetailDto.Right> rights = sut.getRights(USER, "scheduled", null);

        assertThat(rights).containsExactly(
            new NamuAccountDetailDto.Right("005930", "가람전자", "01", "배당", "2026-12-30", "100", null, null),
            new NamuAccountDetailDto.Right("000660", "별빛반도체", "05", "신주인수권증서", null, "1000", null, null));
    }

    @Test
    @DisplayName("권리 지난 것 — rightsHeld · 시작일은 올해 1월 1일(고정 시계) · 유형 이름 없음 · 전체 유형")
    void rightsHeld() {
        respond(up.expect("/krstock/inquiry/v1/rightsHeld")
            .andExpect(jsonPath("$.Input_0.sta_dt").value("20260101"))
            .andExpect(jsonPath("$.Input_0.act_no").value(ACCT))
            .andExpect(jsonPath("$.Input_0.rit_tp_cd").value("")), """
            {"rsp_cd":"00000","rsp_msg":"ok","Output_0":{"sta_dt":"20260101"},
             "Output_1":[{"iem_cd":"005930","iem_nm":"가람전자","rit_tp_cd":"01","bse_dt":"20260630",
                          "hld_qty":"100","rit_aloc_amt":"36100","aloc_amt_pym_dt":"20260820"}]}
            """);

        assertThat(sut.getRights(USER, "held", null)).containsExactly(
            new NamuAccountDetailDto.Right("005930", "가람전자", "01", null, "2026-06-30", "100", "36100", "2026-08-20"));
    }

    @Test
    @DisplayName("권리 지난 것 — from 을 주면 그 날부터 · 모르는 kind 는 400 · 계좌가 없으면 빈 목록")
    void rightsHeldFromAndInvalidKind() {
        respond(up.expect("/krstock/inquiry/v1/rightsHeld")
            .andExpect(jsonPath("$.Input_0.sta_dt").value("20260401")),
            "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"조회할 내역이 없습니다.\"}");
        assertThat(sut.getRights(USER, "held", LocalDate.of(2026, 4, 1))).isEmpty();

        assertThatThrownBy(() -> sut.getRights(USER, "all", null)).isInstanceOf(InvalidValueException.class);

        given(namuQueryService.resolveAccountNo(8L, null)).willReturn(null);
        assertThat(sut.getRights(8L, "scheduled", null)).isEmpty();
        up.server.verify();
    }

    // === 연속조회 (cts · cts_flag 는 응답 헤더) ===

    private static org.springframework.test.web.client.ResponseCreator page(String json, String cts, String flag) {
        org.springframework.test.web.client.response.DefaultResponseCreator c =
            withSuccess(json, org.springframework.http.MediaType.APPLICATION_JSON);
        if (cts != null) {
            c.header("cts", cts);
        }
        if (flag != null) {
            c.header("cts_flag", flag);
        }
        return c;
    }

    private static String krPnl(String symbol, String profit) {
        return "{\"rsp_cd\":\"00166\",\"rsp_msg\":\"ok\",\"Output_0\":{\"pls_amt\":\"999\"},"
            + "\"Output_1\":[{\"iem_cd\":\"" + symbol + "\",\"iem_nm\":\"" + symbol
            + "\",\"sll_qty\":\"1\",\"pls_amt\":\"" + profit + "\",\"pft_rt\":\"1\"}]}";
    }

    @Test
    @DisplayName("연속조회 — cts_flag Y 면 직전 cts · cts_flag 를 요청 헤더에 실어 다음 쪽을 모은다")
    void followsContinuation() {
        up.expect("/krstock/inquiry/v1/tradingPnl")
            .andExpect(request -> assertThat(request.getHeaders().containsHeader("cts")).isFalse()) // 첫 쪽엔 cts 없음
            .andRespond(page(krPnl("A", "10"), "KEY-1", "Y"));
        up.expect("/krstock/inquiry/v1/tradingPnl")
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("cts", "KEY-1"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("cts_flag", "Y"))
            .andRespond(page(krPnl("B", "20"), "KEY-1-END", "N"));
        respond(up.expect("/gbstock/inquiry/v1/periodPnl"), "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"조회할 내역이 없습니다.\"}");

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krItems()).extracting(NamuAccountDetailDto.KrPnlItem::symbol).containsExactly("A", "B");
        assertThat(r.krTotal()).as("요약은 처음 온 것").isEqualTo("999");
        assertThat(r.truncated()).isFalse();
        up.server.verify();
    }

    @Test
    @DisplayName("연속조회 — 최대 쪽 수에서 멈추고 truncated 로 알린다 (조용히 덜 세지 않는다)")
    void stopsAtMaxPages() {
        up.properties.setMaxContinuationPages(2);
        up.expect("/krstock/inquiry/v1/tradingPnl").andRespond(page(krPnl("A", "10"), "K1", "Y"));
        up.expect("/krstock/inquiry/v1/tradingPnl").andRespond(page(krPnl("B", "20"), "K2", "Y"));
        respond(up.expect("/gbstock/inquiry/v1/periodPnl"), "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"x\"}");

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krItems()).hasSize(2);
        assertThat(r.truncated()).isTrue();
        up.server.verify();
    }

    @Test
    @DisplayName("연속조회 — cts 가 직전과 같으면 중단한다 (무한 루프 방지)")
    void stopsWhenCtsRepeats() {
        up.expect("/krstock/inquiry/v1/tradingPnl").andRespond(page(krPnl("A", "10"), "SAME", "Y"));
        up.expect("/krstock/inquiry/v1/tradingPnl").andRespond(page(krPnl("B", "20"), "SAME", "Y"));
        respond(up.expect("/gbstock/inquiry/v1/periodPnl"), "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"x\"}");

        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 23));

        assertThat(r.krItems()).hasSize(2);
        assertThat(r.truncated()).isFalse();
        up.server.verify();
    }

    @Test
    @DisplayName("권리 지난 것 · 예정도 연속조회를 따른다")
    void rightsFollowContinuation() {
        up.expect("/krstock/inquiry/v1/rightsScheduled").andRespond(page(
            "{\"Output_0\":[{\"iem_cd\":\"A\",\"rit_tp_cd\":\"01\"}]}", "R1", "Y"));
        up.expect("/krstock/inquiry/v1/rightsScheduled")
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("cts", "R1"))
            .andRespond(page("{\"Output_0\":[{\"iem_cd\":\"B\",\"rit_tp_cd\":\"01\"}]}", "R2", "N"));

        assertThat(sut.getRights(USER, "scheduled", null)).extracting(NamuAccountDetailDto.Right::symbol)
            .containsExactly("A", "B");
        up.server.verify();
    }

    @Test
    @DisplayName("0건 — 성공 코드에 블록이 없으면 빈 값 (권리 예정 없음 · 매매 없는 기간 · 자산 응답 비어 있음)")
    void emptyIsNotError() {
        String none = "{\"rsp_cd\":\"00000\",\"rsp_msg\":\"\"}";
        respond(up.expect("/krstock/inquiry/v1/rightsScheduled"), none);
        respond(up.expect("/krstock/inquiry/v1/tradingPnl"), none);
        respond(up.expect("/gbstock/inquiry/v1/periodPnl"), none);
        respond(up.expect("/krstock/inquiry/v1/assetStatus"), none);

        assertThat(sut.getRights(USER, "scheduled", null)).isEmpty();
        NamuAccountDetailDto.RealizedPnl r = sut.getRealizedPnl(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 23));
        assertThat(r.krItems()).isEmpty();
        assertThat(r.usRows()).isEmpty();
        assertThat(r.krTotal()).isNull();
        assertThat(sut.getAssetStatus(USER).totalAssets()).isNull();
        up.server.verify();
    }
}
