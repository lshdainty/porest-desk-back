package com.porest.desk.namu.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.service.NamuQueryService;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.securities.service.dto.PriceQuote;
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
 * 1차 범위는 읽기 전용 시세뿐이다. 나무는 실제 체결이 나가는 운영 도메인 하나뿐이라
 * 주문 계열은 붙이지 않는다.
 */
@RestController
@RequestMapping("/api/v1/namu")
@RequiredArgsConstructor
public class NamuApiController {

    private final NamuQueryService namuQueryService;

    /** 국내주식 현재가. marketCode: KRX(기본) · NXT · UNT */
    @GetMapping("/kr/price")
    public ApiResponse<PriceQuote> getKrPrice(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol,
            @RequestParam(required = false) String marketCode) {
        return ApiResponse.success(namuQueryService.getKrPrice(loginUser.getRowId(), symbol, marketCode));
    }

    /** 해외주식 현재가. */
    @GetMapping("/gb/price")
    public ApiResponse<PriceQuote> getGbPrice(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol) {
        return ApiResponse.success(namuQueryService.getGbPrice(loginUser.getRowId(), symbol));
    }

    /** 본인 계좌 목록. 잔고 조회의 계좌번호를 여기서 얻는다. */
    @GetMapping("/accounts")
    public ApiResponse<List<NamuAccountDto.Account>> getAccounts(@LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(namuQueryService.getAccounts(loginUser.getRowId()));
    }

    /**
     * 보유 종목. 국내와 해외는 나무 쪽 엔드포인트·필드명이 다르지만 응답은 한 모양이다.
     *
     * @param currency  KRW 면 국내, 그 밖(USD·CNY·HKD·JPY)이면 해외. 기본 KRW
     * @param accountNo 미지정이면 첫 계좌
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
