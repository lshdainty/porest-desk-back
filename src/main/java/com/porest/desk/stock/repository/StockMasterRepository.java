package com.porest.desk.stock.repository;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.type.StockMarket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface StockMasterRepository {
    /** 유효 종목 검색 (is_active=Y). 검색어가 없으면 시장·심볼 순 전체 페이징 */
    Page<StockMaster> search(StockMasterSearchCondition condition, Pageable pageable);

    /**
     * 해당 시장 전체를 비활성·삭제분까지 포함해 조회한다.
     *
     * <p>동기화 대조용. 삭제·비활성 행을 빼고 보면 (market, symbol) 유니크 제약에 걸려 재적재가 실패한다.
     */
    List<StockMaster> findAllByMarketIncludingInactive(StockMarket market);

    long countAll();

    /** (시장, 심볼) 정확 일치 유효 종목 1건 */
    Optional<StockMaster> findActiveByMarketAndSymbol(StockMarket market, String symbol);

    /** 심볼 정확 일치 유효 종목 전부 — 국내 005930·상해 600519 처럼 시장 간 충돌 가능 */
    List<StockMaster> findAllActiveBySymbol(String symbol);

    StockMaster save(StockMaster stockMaster);
}
