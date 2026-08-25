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

    /**
     * <b>저장할</b> 시장코드를 확정한다. 조회용 {@link #resolve}와 규칙이 다르다.
     *
     * <p>조회는 답을 하나 내야 하므로 후보가 여럿이면 우선순위로 고른다. 하지만 저장은
     * 그렇게 하면 안 된다 — 추측한 값이 컬럼에 눌러앉아 다음부터 확정값 행세를 하고,
     * 사용자에게 다시 물을 기회가 영영 사라진다. 마이그레이션
     * ({@code V2026.08.24_04})도 같은 이유로 심볼이 여러 시장에 걸린 행은
     * {@code HAVING COUNT(DISTINCT m.market_code) = 1}로 걸러 NULL 로 남겼다.
     *
     * @param marketCode 클라이언트가 보낸 시장코드. 알아들을 수 있으면 이게 이긴다 —
     *                   사용자가 종목 검색에서 고른 값이라 유일하게 확실한 정보다.
     * @param symbol     종목 심볼
     * @return 확정된 시장코드. 심볼이 없거나 · 마스터에 없거나 · 여러 시장에 걸리면 null
     */
    public String confirmMarketCode(String marketCode, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        StockMarket given = toMarket(marketCode);
        if (given != null) {
            return given.name();
        }
        return resolveUnique(symbol).map(s -> s.getMarketCode().name()).orElse(null);
    }

    /**
     * 심볼만으로 종목이 <b>하나로 확정될 때만</b> 답한다. 후보가 여럿이면 비어 있다.
     *
     * <p>{@link #resolve}와 달리 우선순위로 고르지 않는다. 고르는 쪽이 맞는 자리
     * (관심목록·평가)와 비워 두는 쪽이 맞는 자리(저장)가 따로 있어서 메서드를 나눴다.
     */
    public Optional<StockMaster> resolveUnique(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String sym = symbol.trim();
        List<StockMaster> candidates = stockMasterRepository.findAllActiveBySymbol(sym);
        if (candidates.size() != 1) {
            log.debug("시장코드 확정 보류 - symbol={}, 후보={}", sym, candidates.size());
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
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
