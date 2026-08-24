package com.porest.desk.stock.service;

import com.porest.desk.stock.client.MasterFileClient;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.MasterFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 마스터파일 동기화를 묶어 실행한다.
 *
 * <p>트랜잭션은 {@link MasterFileSynchronizer} 가 파일 단위로 잡으므로 여기서는 열지 않는다.
 * 한 파일이 실패해도 나머지는 그대로 진행한다.
 *
 * <p><b>순서가 의미를 갖는다.</b> {@code MasterFile} 선언 순서대로 도는데, KIS 파일이 앞이고
 * NH 가 뒤다. NH 는 KIS 가 만든 행을 찾아 보강하므로 먼저 돌면 붙일 행이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterSyncServiceImpl implements StockMasterSyncService {

    private final MasterFileClient masterFileClient;
    private final MasterFileSynchronizer fileSynchronizer;

    @Override
    public List<StockMasterSyncResult> syncAll() {
        List<StockMasterSyncResult> results = new ArrayList<>();
        for (MasterFile file : MasterFile.values()) {
            try {
                List<InstrumentRecord> records = masterFileClient.fetch(file);
                results.add(fileSynchronizer.sync(file, records));
            } catch (RuntimeException e) {
                // 한 파일의 다운로드·DB 오류가 나머지 파일까지 막지 않도록 삼킨다.
                log.error("종목 마스터 동기화 중 오류: file={}", file, e);
                results.add(StockMasterSyncResult.failed(file));
            }
        }
        return results;
    }
}
