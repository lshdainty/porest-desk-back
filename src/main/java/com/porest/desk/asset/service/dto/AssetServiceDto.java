package com.porest.desk.asset.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.type.AssetType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AssetServiceDto {

    // === Asset ===
    public record CreateAssetCommand(
        Long userRowId,
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        Integer sortOrder
    ) {}

    public record UpdateAssetCommand(
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        YNType isIncludedInTotal
    ) {}

    public record AssetInfo(
        Long rowId,
        Long userRowId,
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        YNType isIncludedInTotal,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static AssetInfo from(Asset asset) {
            return new AssetInfo(
                asset.getRowId(),
                asset.getUser().getRowId(),
                asset.getAssetName(),
                asset.getAssetType(),
                asset.getBalance(),
                asset.getCurrency(),
                asset.getIcon(),
                asset.getColor(),
                asset.getInstitution(),
                asset.getMemo(),
                asset.getSortOrder(),
                asset.getIsIncludedInTotal(),
                asset.getCreateAt(),
                asset.getModifyAt()
            );
        }
    }

    public record ReorderItem(
        Long assetId,
        Integer sortOrder
    ) {}

    public record AssetSummary(
        Long totalBalance,
        List<AssetTypeSummary> byType
    ) {}

    public record AssetTypeSummary(
        AssetType assetType,
        Long totalBalance,
        Integer count
    ) {}

    // === Asset Transfer ===
    public record CreateTransferCommand(
        Long userRowId,
        Long fromAssetRowId,
        Long toAssetRowId,
        Long amount,
        Long fee,
        String description,
        LocalDate transferDate
    ) {}

    public record TransferInfo(
        Long rowId,
        Long userRowId,
        Long fromAssetRowId,
        String fromAssetName,
        Long toAssetRowId,
        String toAssetName,
        Long amount,
        Long fee,
        String description,
        LocalDate transferDate,
        LocalDateTime createAt
    ) {
        public static TransferInfo from(AssetTransfer transfer) {
            return new TransferInfo(
                transfer.getRowId(),
                transfer.getUser().getRowId(),
                transfer.getFromAsset().getRowId(),
                transfer.getFromAsset().getAssetName(),
                transfer.getToAsset().getRowId(),
                transfer.getToAsset().getAssetName(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getDescription(),
                transfer.getTransferDate(),
                transfer.getCreateAt()
            );
        }
    }
}
