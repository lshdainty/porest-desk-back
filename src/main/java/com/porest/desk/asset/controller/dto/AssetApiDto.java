package com.porest.desk.asset.controller.dto;

import java.math.BigDecimal;
import com.porest.desk.asset.type.HoldingType;
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
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        YNType isIncludedInTotal,
        Long cardCatalogRowId,
        Long creditLimit,
        Integer paymentDay,
        Long paymentAssetRowId,
        // 투자 보유 목록 (INVESTMENT 전용)
        List<HoldingRequest> holdings
    ) {}

    public record UpdateAssetRequest(
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        String color,
        String institution,
        String memo,
        YNType isIncludedInTotal,
        Long cardCatalogRowId,
        Long creditLimit,
        Integer paymentDay,
        Long paymentAssetRowId,
        // 투자 보유 목록 — null=무변경, 리스트=전체 교체
        List<HoldingRequest> holdings
    ) {}

    /**
     * 투자 보유 입력 — linked=true: tossSymbol+quantity / false: holdingName+holdingValue(quantity 선택).
     * holdingType 미지정은 STOCK 으로 본다(구버전 클라이언트 하위호환).
     */
    public record HoldingRequest(
        HoldingType holdingType,
        Boolean linked,
        String tossSymbol,
        BigDecimal quantity,
        String holdingName,
        Long holdingValue
    ) {
        public AssetServiceDto.HoldingCommand toCommand() {
            return new AssetServiceDto.HoldingCommand(
                holdingType, linked, tossSymbol, quantity, holdingName, holdingValue);
        }

        public static List<AssetServiceDto.HoldingCommand> toCommands(List<HoldingRequest> requests) {
            return requests == null ? null : requests.stream().map(HoldingRequest::toCommand).toList();
        }
    }

    public record HoldingResponse(
        Long rowId,
        HoldingType holdingType,
        boolean linked,
        String tossSymbol,
        BigDecimal quantity,
        String holdingName,
        Long holdingValue,
        Integer sortOrder
    ) {
        public static HoldingResponse from(AssetServiceDto.HoldingInfo h) {
            return new HoldingResponse(
                h.rowId(), h.holdingType(), h.linked(), h.tossSymbol(), h.quantity(),
                h.holdingName(), h.holdingValue(), h.sortOrder());
        }
    }

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
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        YNType isIncludedInTotal,
        CardCatalogBriefResponse cardCatalog,
        Long creditLimit,
        Integer paymentDay,
        Long paymentAssetRowId,
        String tossSymbol,
        Long tossQuantity,
        // 투자 보유 목록 (INVESTMENT 외/보유 없음 = 빈 배열)
        List<HoldingResponse> holdings,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static AssetResponse from(AssetServiceDto.AssetInfo info) {
            return new AssetResponse(
                info.rowId(), info.userRowId(), info.assetName(), info.assetType(),
                info.balance(), info.currency(), info.color(),
                info.institution(), info.memo(), info.sortOrder(), info.isIncludedInTotal(),
                CardCatalogBriefResponse.from(info.cardCatalog()),
                info.creditLimit(), info.paymentDay(), info.paymentAssetRowId(),
                info.tossSymbol(), info.tossQuantity(),
                info.holdings().stream().map(HoldingResponse::from).toList(),
                info.createAt(), info.modifyAt()
            );
        }
    }

    /** 투자 자산 ↔ 토스 종목 연결 요청 (종목코드 + 보유수량). 평가액 = 토스 시세 × 수량. */
    public record TossLinkRequest(
        String symbol,
        Long quantity
    ) {}

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

    public record NetWorthTrendPointResponse(Integer year, Integer month, Long netWorth) {
        public static NetWorthTrendPointResponse from(AssetServiceDto.NetWorthTrendPoint p) {
            return new NetWorthTrendPointResponse(p.year(), p.month(), p.netWorth());
        }
    }

    public record NetWorthTrendResponse(List<NetWorthTrendPointResponse> trend) {
        public static NetWorthTrendResponse from(List<AssetServiceDto.NetWorthTrendPoint> points) {
            return new NetWorthTrendResponse(points.stream().map(NetWorthTrendPointResponse::from).toList());
        }
    }

    public record AssetBalancePointResponse(java.time.LocalDate weekStart, Long balance) {
        public static AssetBalancePointResponse from(AssetServiceDto.AssetBalancePoint p) {
            return new AssetBalancePointResponse(p.weekStart(), p.balance());
        }
    }

    public record AssetBalanceTrendResponse(List<AssetBalancePointResponse> trend) {
        public static AssetBalanceTrendResponse from(List<AssetServiceDto.AssetBalancePoint> points) {
            return new AssetBalanceTrendResponse(points.stream().map(AssetBalancePointResponse::from).toList());
        }
    }

    // === Asset Transfer ===
    public record CreateTransferRequest(
        Long fromAssetRowId,
        Long toAssetRowId,
        Long amount,
        Long fee,
        String description,
        LocalDateTime transferDate
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
        LocalDateTime transferDate,
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
