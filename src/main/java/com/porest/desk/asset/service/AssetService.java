package com.porest.desk.asset.service;

import com.porest.desk.asset.service.dto.AssetServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface AssetService {
    AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command);
    List<AssetServiceDto.AssetInfo> getAssets(Long userRowId);
    AssetServiceDto.AssetInfo getAsset(Long assetId, Long userRowId);
    AssetServiceDto.AssetInfo updateAsset(Long assetId, Long userRowId, AssetServiceDto.UpdateAssetCommand command);
    void deleteAsset(Long assetId, Long userRowId);

    /** 투자 자산을 토스 종목에 연결 (종목코드+보유수량, 프로+토스 연결 사용자 전용, INVESTMENT 자산만). */
    AssetServiceDto.AssetInfo linkTossSymbol(Long assetId, Long userRowId, String symbol, Long quantity);

    /** 토스 연결 해제 — 다시 수동 입력 잔액으로 복귀. */
    AssetServiceDto.AssetInfo unlinkTossSymbol(Long assetId, Long userRowId);

    /** 토스 연결 종목들의 시세×수량 평가액을 일 1회 VALUATION 앵커로 적재(추이 반영). 스케줄러용. */
    void snapshotTossValuations();
    AssetServiceDto.AssetSummary getAssetSummary(Long userRowId, Integer year, Integer month);
    List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months);
    List<AssetServiceDto.AssetBalancePoint> getAssetBalanceTrend(Long assetId, Long userRowId, Integer weeks);
    void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items);

    AssetServiceDto.TransferInfo createTransfer(AssetServiceDto.CreateTransferCommand command);
    List<AssetServiceDto.TransferInfo> getTransfers(Long userRowId, LocalDate startDate, LocalDate endDate);
    /** 이체 수정 — 이자 지출·잔액 이력을 되돌렸다 다시 만든다. rowId 는 유지된다. */
    AssetServiceDto.TransferInfo updateTransfer(Long transferId, AssetServiceDto.CreateTransferCommand command);

    void deleteTransfer(Long transferId, Long userRowId);

    /** 사용자가 누른 삭제 — 시스템이 만든 이체(매매 충당·카드 결제)는 막는다. */
    void deleteTransferByUser(Long transferId, Long userRowId);

    /**
     * 그 사용자의 모든 자산 잔액을 이력에서 다시 계산한다 (관리 수단).
     *
     * <p>잔액은 asset_balance_history 가 진실이고 asset 의 컬럼은 파생 캐시다. 스키마가 바뀌어
     * 캐시가 비었거나(신규 컬럼은 DEFAULT 0) 이력을 직접 손봐 어긋났을 때 되맞춘다.
     * 몇 번 돌려도 결과가 같다.
     *
     * @return 다시 계산한 자산 수
     */
}
