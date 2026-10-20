package io.groo.damda.stock.service;

import io.groo.damda.stock.service.dto.StockMasterSyncResult;

import java.util.List;

public interface StockMasterSyncService {
    /** 15개 시장 전체를 동기화한다. 한 시장이 실패해도 나머지는 계속 진행한다. */
    List<StockMasterSyncResult> syncAll();
}
