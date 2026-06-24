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

    /** 투자 자산을 토스 보유종목에 연결 (프로+토스 연결 사용자 전용, INVESTMENT 자산만). */
    AssetServiceDto.AssetInfo linkTossSymbol(Long assetId, Long userRowId, Long accountSeq, String symbol);

    /** 토스 연결 해제 — 다시 수동 입력 잔액으로 복귀. */
    AssetServiceDto.AssetInfo unlinkTossSymbol(Long assetId, Long userRowId);

    /** 토스 연결 투자 자산의 평가액을 하루 1회 VALUATION 스냅샷으로 적재(순자산 추이 반영, 스케줄러용). */
    void snapshotTossValuations();
    AssetServiceDto.AssetSummary getAssetSummary(Long userRowId, Integer year, Integer month);
    List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months);
    List<AssetServiceDto.AssetBalancePoint> getAssetBalanceTrend(Long assetId, Long userRowId, Integer weeks);
    void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items);

    AssetServiceDto.TransferInfo createTransfer(AssetServiceDto.CreateTransferCommand command);
    List<AssetServiceDto.TransferInfo> getTransfers(Long userRowId, LocalDate startDate, LocalDate endDate);
    void deleteTransfer(Long transferId, Long userRowId);
}
