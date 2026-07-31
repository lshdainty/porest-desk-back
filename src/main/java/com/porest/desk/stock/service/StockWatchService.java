package com.porest.desk.stock.service;

import com.porest.desk.stock.service.dto.StockWatchServiceDto;
import com.porest.desk.stock.type.StockMarket;

import java.util.List;

public interface StockWatchService {
    /** 사용자의 관심목록 그룹 전체 (소속 종목·마스터 정보 포함) */
    List<StockWatchServiceDto.GroupInfo> getGroups(Long userRowId);

    StockWatchServiceDto.GroupInfo createGroup(Long userRowId, String groupName);

    StockWatchServiceDto.GroupInfo renameGroup(Long userRowId, Long groupRowId, String groupName);

    void deleteGroup(Long userRowId, Long groupRowId);

    /**
     * 그룹에 종목 추가. 종목은 마스터에서 해석한다 —
     * marketCode 미지정 시 심볼 정확 일치 중 토스 시세 대상(KR/US) 시장을 우선한다.
     * 이미 담긴 종목이면 그대로 반환한다(멱등).
     */
    StockWatchServiceDto.ItemInfo addItem(Long userRowId, Long groupRowId, String symbol, StockMarket marketCode);

    void removeItem(Long userRowId, Long itemRowId);
}
