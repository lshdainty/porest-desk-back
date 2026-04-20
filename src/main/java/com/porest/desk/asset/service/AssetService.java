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
    AssetServiceDto.AssetSummary getAssetSummary(Long userRowId);
    List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months);
    void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items);

    AssetServiceDto.TransferInfo createTransfer(AssetServiceDto.CreateTransferCommand command);
    List<AssetServiceDto.TransferInfo> getTransfers(Long userRowId, LocalDate startDate, LocalDate endDate);
    void deleteTransfer(Long transferId, Long userRowId);
}
