package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamuQueryServiceImpl implements NamuQueryService {

    private static final String KR_PRICE_PATH = "/krstock/quote/v1/currentPrice";
    private static final String GB_PRICE_PATH = "/gbstock/quote/v1/current";

    /** 국내 거래소 기본값. NXT 거래 종목도 KRX 로 물으면 정규시장 시세가 온다. */
    private static final String MARKET_KRX = "KRX";
    private static final String KRW = "KRW";

    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.KrPrice>> KR_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.GbPrice>> GB_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final NamuApiClient namuApiClient;
    private final StockMasterRepository stockMasterRepository;

    @Override
    public PriceQuote getKrPrice(Long userRowId, String symbol, String marketCode) {
        List<NamuMarketDto.KrPrice> out = namuApiClient.post(userRowId, KR_PRICE_PATH,
            Map.of("market_cd", marketCode == null || marketCode.isBlank() ? MARKET_KRX : marketCode,
                   "iem_cd", symbol),
            KR_TYPE);
        return out.isEmpty() ? null : quote(symbol, out.get(0).price(), KRW);
    }

    @Override
    public PriceQuote getGbPrice(Long userRowId, String symbol) {
        List<NamuMarketDto.GbPrice> out = namuApiClient.post(userRowId, GB_PRICE_PATH,
            Map.of("iem_cd", symbol), GB_TYPE);
        if (out.isEmpty()) {
            return null;
        }
        NamuMarketDto.GbPrice p = out.get(0);
        return quote(symbol, p.price(), p.currency());
    }

    /**
     * 국내·해외를 섞어 조회한다.
     *
     * <p>나무는 <b>종목 다건 한 번에</b> 주는 시세 API 가 없어 종목마다 한 콜씩 나간다
     * (토스는 콤마 구분 다건이다). 보유 종목 수만큼 호출이 나가므로 자산 평가 경로에서만 쓰고,
     * 한 종목이 실패해도 나머지는 살린다 — 전체를 접으면 평가가 통째로 멈춘다.
     *
     * <p>국내인지 해외인지는 {@code stock_master} 가 정한다. 심볼이 여러 시장에 걸치면
     * (해외 중복 티커) 어느 쪽인지 확정할 수 없어 건너뛴다 — 엉뚱한 시장 시세를 자산에
     * 반영하느니 그 종목만 빼는 게 낫다.
     */
    @Override
    public List<PriceQuote> getPrices(Long userRowId, List<String> symbols) {
        List<PriceQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            List<StockMaster> matches = stockMasterRepository.findAllActiveBySymbol(symbol);
            if (matches.size() != 1) {
                log.debug("나무 시세 건너뜀 - 종목 특정 불가: symbol={}, 후보={}건", symbol, matches.size());
                continue;
            }
            StockMaster stock = matches.get(0);
            try {
                PriceQuote quote = "KR".equals(stock.getCountryCode())
                    ? getKrPrice(userRowId, symbol, MARKET_KRX)
                    : getGbPrice(userRowId, symbol);
                if (quote != null) {
                    quotes.add(quote);
                }
            } catch (RuntimeException e) {
                log.warn("나무 시세 조회 실패 - symbol={}: {}", symbol, e.getMessage());
            }
        }
        return quotes;
    }

    private static PriceQuote quote(String symbol, String rawPrice, String currency) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        try {
            return new PriceQuote(symbol, new BigDecimal(rawPrice.trim()),
                currency == null || currency.isBlank() ? KRW : currency);
        } catch (NumberFormatException e) {
            log.warn("나무 시세 파싱 실패: symbol={}, price={}", symbol, rawPrice);
            return null;
        }
    }
}
