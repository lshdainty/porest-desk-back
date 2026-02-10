package com.porest.desk.asset.service;

import com.porest.desk.asset.service.dto.AssetServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface AssetService {
    AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command);
    List<AssetServiceDto.AssetInfo> getAssets(Long userRowId);
    AssetServiceDto.AssetInfo getAsset(Long assetId);
    AssetServiceDto.AssetInfo updateAsset(Long assetId, AssetServiceDto.UpdateAssetCommand command);
    void deleteAsset(Long assetId);
    AssetServiceDto.AssetSummary getAssetSummary(Long userRowId);
    void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items);

    AssetServiceDto.TransferInfo createTransfer(AssetServiceDto.CreateTransferCommand command);
    List<AssetServiceDto.TransferInfo> getTransfers(Long userRowId, LocalDate startDate, LocalDate endDate);
    void deleteTransfer(Long transferId);
}
