package com.porest.desk.stock.service;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 심볼(+시장)로 종목 마스터 1건을 특정한다. <b>같은 심볼은 어디서 물어도 같은 종목으로 풀려야 한다.</b>
 *
 * <p>관심목록과 자산 평가가 각자 다른 규칙으로 풀면, 사용자가 관심목록에 담은 SPY 와 자산에
 * 연결한 SPY 가 다른 행을 가리키게 된다. 그래서 규칙을 한 곳에 둔다.
 *
 * <p><b>왜 이 규칙인가</b> — NH 소스를 붙이며 시장이 6개 늘었다(ASX·GER·LSE·JKT·BTQ·PNK).
 * 그 결과 같은 티커가 여러 시장에 걸리는 경우가 크게 늘었다(SPY·IVV·JEPI·SOXL 등).
 * 시장을 안 알려주면 후보 중 하나를 골라야 하는데, 아무거나 고르면 런던 상장 SPY 시세로
 * 미국 보유분을 평가하게 된다.
 *
 * <ol>
 *   <li><b>시장을 알려주면 그걸 쓴다</b> — 유일하게 확실한 정보다.</li>
 *   <li>모르면 <b>KIS 소유 시장</b>을 먼저 본다. NH 가 시장을 늘리기 전부터 있던 것들이라,
 *       그때 만들어진 연결의 의도가 여기 있다.</li>
 *   <li>그중에서도 국내·미국을 먼저 본다(서비스가 실제로 다루는 대부분).</li>
 *   <li>그래도 남으면 <b>시장 enum 선언 순서</b>로 고정한다. DB 반환 순서에 기대면
 *       같은 질의가 배포마다 다른 답을 낼 수 있다.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMasterResolver {

    /** 서비스가 실제로 다루는 시장. 후보가 남았을 때 먼저 본다. */
    private static final Set<String> PREFERRED_COUNTRIES = Set.of("KR", "US");

    private final StockMasterRepository stockMasterRepository;

    /** 시장을 알면 정확 매칭, 모르면 위 우선순위로 하나를 고른다. 못 찾으면 비어 있다. */
    public Optional<StockMaster> resolve(StockMarket marketCode, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String sym = symbol.trim();

        if (marketCode != null) {
            return stockMasterRepository.findActiveByMarketAndSymbol(marketCode, sym);
        }

        List<StockMaster> candidates = stockMasterRepository.findAllActiveBySymbol(sym);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            log.debug("종목 심볼이 여러 시장에 걸린다 - symbol={}, 후보={}", sym,
                candidates.stream().map(s -> s.getMarketCode().name()).toList());
        }
        return candidates.stream().min(PRIORITY);
    }

    /** 시장을 문자열로 받는 자리(자산 연동 컬럼)용. 모르는 코드는 시장 미지정으로 본다. */
    public Optional<StockMaster> resolve(String marketCode, String symbol) {
        return resolve(toMarket(marketCode), symbol);
    }

    private static StockMarket toMarket(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            return null;
        }
        try {
            return StockMarket.valueOf(marketCode.trim());
        } catch (IllegalArgumentException e) {
            // 앱이 앞서 나가 우리가 모르는 시장을 보낼 수 있다 — 미지정으로 떨어뜨린다.
            return null;
        }
    }

    /** 낮을수록 먼저. 마지막 단계가 enum 선언 순서라 항상 하나로 확정된다. */
    private static final Comparator<StockMaster> PRIORITY =
        Comparator.<StockMaster>comparingInt(s -> s.getMarketCode().isOwnedBy(MasterSource.KIS) ? 0 : 1)
            .thenComparingInt(s -> PREFERRED_COUNTRIES.contains(s.getCountryCode()) ? 0 : 1)
            .thenComparing(s -> s.getMarketCode().ordinal());
}
