package com.porest.desk.asset.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AssetApiDto {

    // === Asset ===
    public record CreateAssetRequest(
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        Long cardCatalogRowId
    ) {}

    public record UpdateAssetRequest(
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        YNType isIncludedInTotal,
        Long cardCatalogRowId
    ) {}

    public record CardCatalogBriefResponse(
        Long rowId,
        String cardName,
        String imgUrl,
        String companyName,
        String companyLogoUrl
    ) {
        public static CardCatalogBriefResponse from(AssetServiceDto.CardCatalogBrief b) {
            if (b == null) return null;
            return new CardCatalogBriefResponse(b.rowId(), b.cardName(), b.imgUrl(), b.companyName(), b.companyLogoUrl());
        }
    }

    public record AssetResponse(
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
        CardCatalogBriefResponse cardCatalog,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static AssetResponse from(AssetServiceDto.AssetInfo info) {
            return new AssetResponse(
                info.rowId(), info.userRowId(), info.assetName(), info.assetType(),
                info.balance(), info.currency(), info.icon(), info.color(),
                info.institution(), info.memo(), info.sortOrder(), info.isIncludedInTotal(),
                CardCatalogBriefResponse.from(info.cardCatalog()),
                info.createAt(), info.modifyAt()
            );
        }
    }

    public record AssetListResponse(List<AssetResponse> assets) {
        public static AssetListResponse from(List<AssetServiceDto.AssetInfo> infos) {
            return new AssetListResponse(infos.stream().map(AssetResponse::from).toList());
        }
    }

    public record ReorderRequest(List<ReorderItem> items) {}

    public record ReorderItem(Long assetId, Integer sortOrder) {}

    public record AssetSummaryResponse(
        Long totalBalance,
        Long totalAssets,
        Long totalDebt,
        Long netWorth,
        Long lastMonthNetWorth,
        Long changeAmount,
        Double changePercent,
        List<AssetTypeSummaryResponse> byType
    ) {
        public static AssetSummaryResponse from(AssetServiceDto.AssetSummary summary) {
            return new AssetSummaryResponse(
                summary.totalBalance(),
                summary.totalAssets(),
                summary.totalDebt(),
                summary.netWorth(),
                summary.lastMonthNetWorth(),
                summary.changeAmount(),
                summary.changePercent(),
                summary.byType().stream().map(AssetTypeSummaryResponse::from).toList()
            );
        }
    }

    public record AssetTypeSummaryResponse(AssetType assetType, Long totalBalance, Integer count) {
        public static AssetTypeSummaryResponse from(AssetServiceDto.AssetTypeSummary s) {
            return new AssetTypeSummaryResponse(s.assetType(), s.totalBalance(), s.count());
        }
    }

    // === Asset Transfer ===
    public record CreateTransferRequest(
        Long fromAssetRowId,
        Long toAssetRowId,
        Long amount,
        Long fee,
        String description,
        LocalDate transferDate
    ) {}

    public record TransferResponse(
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
        public static TransferResponse from(AssetServiceDto.TransferInfo info) {
            return new TransferResponse(
                info.rowId(), info.userRowId(),
                info.fromAssetRowId(), info.fromAssetName(),
                info.toAssetRowId(), info.toAssetName(),
                info.amount(), info.fee(), info.description(),
                info.transferDate(), info.createAt()
            );
        }
    }

    public record TransferListResponse(List<TransferResponse> transfers) {
        public static TransferListResponse from(List<AssetServiceDto.TransferInfo> infos) {
            return new TransferListResponse(infos.stream().map(TransferResponse::from).toList());
        }
    }
}
