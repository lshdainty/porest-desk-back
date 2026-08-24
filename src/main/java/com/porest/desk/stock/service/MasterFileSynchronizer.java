package com.porest.desk.stock.service;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마스터파일 1개를 DB 와 맞춘다. 파일마다 트랜잭션을 끊어 한 파일의 실패가 다른 파일을 되돌리지 않게 한다.
 *
 * <p><b>레코드를 두 갈래로 나눈다.</b>
 *
 * <ul>
 *   <li><b>소유 시장</b>({@code market.isOwnedBy(source)}) — 행을 만들고 고치고, 파일에서 사라지면 비활성화한다.</li>
 *   <li><b>남의 시장</b> — <b>보강만</b> 한다. 두 소스가 같은 종목을 다른 거래소로 분류해서
 *       (KIS AMS 4,657건 중 NH 는 2,700건을 NYY, 1,599건을 BTQ 로 넣는다) 행을 만들면
 *       같은 종목이 두 행으로 갈라지고 사용자가 연결해 둔 자산이 어느 행을 가리키는지 깨진다.</li>
 * </ul>
 *
 * <p>보강 대조는 <b>(국가, 심볼)</b>로 한다. 시장으로 맞추면 위 분류 차이 때문에 못 찾는다.
 * 같은 국가에 같은 심볼이 둘 이상이면 어느 쪽인지 알 수 없으므로 건드리지 않고 센다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterFileSynchronizer {

    private final StockMasterRepository stockMasterRepository;

    /**
     * (market, symbol) 키로 대조해 실제로 달라진 행만 손댄다.
     *
     * <p>전량 삭제 후 재적재하면 변경이 없는 날에도 row_id 와 수정 이력이 매일 갱신되고,
     * 자산이 참조할 마스터 행이 순간적으로 사라지므로 diff upsert 로만 맞춘다.
     */
    @Transactional
    public StockMasterSyncResult sync(MasterFile file, List<InstrumentRecord> records) {
        // 파일이 비정상(빈 응답·레코드 경계 불일치)일 때 전 종목을 비활성화하면 안 된다. 기존 데이터를 유지한다.
        if (records.isEmpty()) {
            log.warn("종목 마스터 동기화 - 파일에 데이터 없음, 기존 데이터 유지: file={}", file);
            return StockMasterSyncResult.failed(file);
        }

        Map<StockMarket, List<InstrumentRecord>> owned = new LinkedHashMap<>();
        List<InstrumentRecord> foreign = new ArrayList<>();
        for (InstrumentRecord record : records) {
            if (record.market().isOwnedBy(file.getSource())) {
                owned.computeIfAbsent(record.market(), m -> new ArrayList<>()).add(record);
            } else {
                foreign.add(record);
            }
        }

        Tally tally = new Tally();
        owned.forEach((market, marketRecords) -> upsertMarket(file, market, marketRecords, tally));
        int enriched = enrich(file, foreign);

        StockMasterSyncResult result = new StockMasterSyncResult(
            file, false, tally.inserted, tally.updated + enriched, tally.deactivated, tally.unchanged);
        if (result.hasChanges()) {
            log.info("종목 마스터 동기화 변경: file={}, 추가={}, 수정={}, 보강={}, 비활성={}",
                file, tally.inserted, tally.updated, enriched, tally.deactivated);
        } else {
            log.debug("종목 마스터 동기화 변경 없음: file={}, 대상={}건", file, tally.unchanged);
        }
        return result;
    }

    private void upsertMarket(MasterFile file, StockMarket market, List<InstrumentRecord> records, Tally tally) {
        Map<String, StockMaster> existingBySymbol = new HashMap<>();
        List<StockMaster> existing = stockMasterRepository.findAllByMarketIncludingInactive(market);
        for (StockMaster stock : existing) {
            existingBySymbol.put(stock.getSymbol(), stock);
        }

        Set<String> fileSymbols = new HashSet<>();
        for (InstrumentRecord record : records) {
            fileSymbols.add(record.symbol());
            StockMaster stock = existingBySymbol.get(record.symbol());

            if (stock == null) {
                stockMasterRepository.save(StockMaster.create(file.getSource(), record));
                tally.inserted++;
                continue;
            }
            // 운영자가 지운 행은 되살리지 않는다. 유니크 제약 때문에 재적재도 할 수 없다.
            if (stock.isDeleted()) {
                tally.unchanged++;
                continue;
            }
            if (stock.syncFrom(record)) {
                tally.updated++;
            } else {
                tally.unchanged++;
            }
        }

        for (StockMaster stock : existing) {
            if (stock.isDeleted() || !stock.isActive() || fileSymbols.contains(stock.getSymbol())) {
                continue;
            }
            // 파일에서 사라진 종목(상장폐지·심볼 변경 등). 자산 연결 보호를 위해 행은 남긴다.
            stock.deactivate();
            tally.deactivated++;
        }
    }

    /**
     * 남의 시장 레코드로 보강 필드만 채운다.
     *
     * <p>대조 키는 (국가, 심볼)이다. 같은 국가에 같은 심볼이 둘 이상이면 어느 종목인지 확정할 수
     * 없어 건드리지 않는다 — 틀린 행에 나무 조회 키를 붙이면 엉뚱한 종목 시세가 화면에 뜬다.
     */
    private int enrich(MasterFile file, List<InstrumentRecord> foreign) {
        if (foreign.isEmpty()) {
            return 0;
        }
        Set<String> countries = new HashSet<>();
        for (InstrumentRecord record : foreign) {
            countries.add(record.market().getCountryCode());
        }

        Map<String, StockMaster> byKey = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (StockMaster stock : stockMasterRepository.findAllActiveByCountryCodeIn(countries)) {
            String key = stock.getCountryCode() + ':' + stock.getSymbol();
            if (byKey.putIfAbsent(key, stock) != null) {
                ambiguous.add(key);
            }
        }

        int enriched = 0;
        int missed = 0;
        for (InstrumentRecord record : foreign) {
            String key = record.market().getCountryCode() + ':' + record.symbol();
            if (ambiguous.contains(key)) {
                continue;
            }
            StockMaster stock = byKey.get(key);
            if (stock == null) {
                missed++;
                continue;
            }
            if (stock.enrichFrom(record)) {
                enriched++;
            }
        }
        // 조용히 넘기지 않는다 — 못 찾은 수가 갑자기 늘면 상대 소스가 종목을 대거 추가했다는 신호다.
        if (missed > 0 || !ambiguous.isEmpty()) {
            log.info("종목 마스터 보강: file={}, 보강={}건, 대상없음={}건, 심볼중복={}건",
                file, enriched, missed, ambiguous.size());
        }
        return enriched;
    }

    private static final class Tally {
        private int inserted;
        private int updated;
        private int deactivated;
        private int unchanged;
    }
}
