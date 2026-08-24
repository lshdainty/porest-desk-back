package com.porest.desk.securities.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.SecuritiesPriceProviders;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 증권사 무관 시세 — <b>사용자가 고른 기본 소스</b>로 대신 조회한다.
 *
 * <p><b>왜 필요한가</b> — 가계부 자산 화면은 보유 종목의 현재가와 환율만 있으면 된다. 그런데
 * 앱·웹이 {@code /api/v1/toss/**} 를 직접 부르고 있어서, <b>나무만 연결한 사용자는 자산
 * 평가액이 0 이나 누락으로 표시됐다</b>(토스 크리덴셜이 없어 403/에러). 서버 쪽 평가는 이미
 * 기본 소스를 따르는데 클라이언트가 안 따라온 것이다.
 *
 * <p>증권사별 조회({@code /api/v1/toss/**} · {@code /api/v1/namu/**})는 그대로 둔다 —
 * 증권 화면이 보여주는 것은 증권사마다 달라 합칠 수 없다. 여기 있는 건
 * <b>두 증권사가 똑같이 주는 것</b>뿐이다.
 *
 * <p>활성 구독(SECURITIES) 필요 — {@code FeatureGateInterceptor} 가 게이트한다.
 */
@RestController
@RequestMapping("/api/v1/securities")
@RequiredArgsConstructor
public class SecuritiesApiController {

    /** 한 번에 물을 수 있는 종목 수. 나무는 종목마다 1콜이라 상한이 없으면 유량 제한에 걸린다. */
    private static final int MAX_SYMBOLS = 50;

    private final SecuritiesPriceProviders priceProviders;

    /**
     * 종목 다건 현재가. 못 구한 종목은 결과에서 빠진다 — 호출부가 빠진 것을 보고 판단한다.
     *
     * @param symbols 콤마 구분 종목코드(stock_master 기준). 시장은 서버가 정한다 —
     *                같은 티커가 여러 시장에 걸리면 {@code StockMasterResolver} 우선순위를 따른다
     */
    @GetMapping("/prices")
    public ApiResponse<List<PriceQuote>> getPrices(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbols) {
        List<InstrumentRef> instruments = Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .limit(MAX_SYMBOLS)
            .map(InstrumentRef::of)
            .toList();
        if (instruments.isEmpty()) {
            return ApiResponse.success(List.of());
        }
        SecuritiesPriceProvider provider = priceProviders.forUser(loginUser.getRowId());
        return ApiResponse.success(provider.getPrices(loginUser.getRowId(), instruments));
    }

    /**
     * 환율. 못 구하면 {@code null} — 나무는 해당 통화 보유 종목이 있어야 환율이 나온다.
     * 호출부는 외화 환산을 접는다(부분합으로 금액을 왜곡하지 않는다).
     */
    @GetMapping("/exchange-rate")
    public ApiResponse<ExchangeRateResponse> getExchangeRate(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "KRW") String quote) {
        BigDecimal rate = priceProviders.forUser(loginUser.getRowId())
            .getFxRate(loginUser.getRowId(), base, quote);
        return ApiResponse.success(new ExchangeRateResponse(base, quote, rate));
    }

    /** @param rate 못 구하면 null */
    public record ExchangeRateResponse(String base, String quote, BigDecimal rate) {
    }
}
