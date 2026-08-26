package com.porest.desk.namu.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.service.NamuQueryService;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.securities.service.dto.PriceQuote;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 나무증권 Open API 조회 프록시.
 *
 * <p>토스({@code /api/v1/toss/**})와 <b>일부러 나눠 둔다.</b> 두 증권사가 주는 데이터가
 * 겹치지 않아 한 경로로 합치면 절반이 "이 증권사는 미지원" 이 된다. 증권 화면도 같은 이유로
 * 증권사별 하위 페이지로 나뉜다.
 *
 * <p>활성 구독(SECURITIES) 필요 — {@code FeatureGateInterceptor} 가 게이트한다.
 * 1차 범위는 읽기 전용이다. 운영 환경에서는 실제 체결이 나가는 도메인을 그대로 쓰므로
 * 주문 계열은 붙이지 않는다.
 */
@RestController
@RequestMapping("/api/v1/namu")
@RequiredArgsConstructor
public class NamuApiController {

    private final NamuQueryService namuQueryService;

    /**
     * 국내주식 현재가.
     *
     * @param marketCode 나무 <b>거래소</b> 코드 — {@code KRX}(기본) · {@code NXT} · {@code UNT}.
     *                   <b>{@code StockMarket.marketCode}({@code KOSPI}·{@code NAS} …)와 이름은 같지만
     *                   다른 어휘다.</b> 여기에 {@code KOSPI} 를 태우면 나무가 모르는 값이라
     *                   400 으로 거절한다 — 예전에는 그 종목만 조용히 빈 응답이 됐다.
     *                   이름을 안 바꾼 이유는 이미 나간 클라이언트가 이 파라미터로 부르고 있어서다
     */
    @GetMapping("/kr/price")
    public ApiResponse<PriceQuote> getKrPrice(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol,
            @RequestParam(required = false)
            @Schema(allowableValues = {"KRX", "NXT", "UNT"}, defaultValue = "KRX") String marketCode) {
        return ApiResponse.success(namuQueryService.getKrPrice(loginUser.getRowId(), symbol, marketCode));
    }

    /** 해외주식 현재가. */
    @GetMapping("/gb/price")
    public ApiResponse<PriceQuote> getGbPrice(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol) {
        return ApiResponse.success(namuQueryService.getGbPrice(loginUser.getRowId(), symbol));
    }

    /**
     * 본인 계좌 목록. 잔고 조회의 계좌번호를 여기서 얻는다.
     *
     * <p><b>거르지 않고 전부 준다.</b> 다만 계좌구분(운영 01·02 / 모의투자 03)이 그 계좌를 쓸 수
     * 있는 도메인을 정하므로, 현재 연동 환경에서 잔고 조회에 쓸 수 있는 계좌만 {@code usable=true}
     * 로 표시된다. 화면은 그걸로 고르면 된다.
     */
    @GetMapping("/accounts")
    public ApiResponse<List<NamuAccountDto.Account>> getAccounts(@LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(namuQueryService.getAccounts(loginUser.getRowId()));
    }

    /**
     * 보유 종목. 국내와 해외는 나무 쪽 엔드포인트·필드명이 다르지만 응답은 한 모양이다.
     *
     * @param currency  KRW 면 국내, USD 면 해외. 기본 KRW.
     *                  <b>해외는 미국만 지원한다</b> — 나무 잔고 조회가 거래국가를 하나만 받는데
     *                  우리는 미국으로 고정했다. JPY·HKD·CNY 를 넘기면 400 이다(미국 계좌에서
     *                  엔화 종목을 찾는 셈이라, 그냥 태우면 업스트림이 조용히 0건을 준다)
     * @param accountNo 미지정이면 현재 환경에서 쓸 수 있는 첫 계좌를 자동으로 고른다.
     *                  지정하면 본인 계좌인지 + 현재 환경에서 쓸 수 있는 구분인지 검증한다
     *                  ({@code usable=false} 인 계좌를 넘기면 400) — 그냥 태우면 업스트림이
     *                  계좌번호 오류로 거절해 502 만 남고 원인을 알 수 없다
     */
    @GetMapping("/holdings")
    public ApiResponse<NamuAccountDto.Holdings> getHoldings(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) String accountNo,
            @RequestParam(defaultValue = "KRW") String currency) {
        return ApiResponse.success(
            namuQueryService.getHoldings(loginUser.getRowId(), accountNo, currency));
    }
}
