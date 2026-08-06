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
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. */
        java.math.BigDecimal exchangeRate,
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
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. */
        java.math.BigDecimal exchangeRate,
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
        /** 기존 보유 행 아이디 — 보내면 제자리 수정돼 원가와 매매 이력 연결이 유지된다. */
        Long rowId,
        HoldingType holdingType,
        Boolean linked,
        String tossSymbol,
        BigDecimal quantity,
        String holdingName,
        Long holdingValue,
        /** 총 매수원가 (원화). 실현손익의 기준 — 미지정이면 0 으로 시작한다. */
        Long totalCost
    ) {
        public AssetServiceDto.HoldingCommand toCommand() {
            return new AssetServiceDto.HoldingCommand(
                rowId, holdingType, linked, tossSymbol, quantity, holdingName, holdingValue, totalCost);
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
        /** 소수 정밀도 보존 — JS number 로 내려가면 코인 0.00012345 같은 값이 흔들린다. */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        BigDecimal quantity,
        String holdingName,
        Long holdingValue,
        /** 총 매수원가 (원화, 수수료 포함). 평가액과의 차이가 평가손익이다. */
        Long totalCost,
        /** 평단가 — 총원가 / 수량. */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        BigDecimal avgPrice,
        Integer sortOrder
    ) {
        public static HoldingResponse from(AssetServiceDto.HoldingInfo h) {
            return new HoldingResponse(
                h.rowId(), h.holdingType(), h.linked(), h.tossSymbol(), h.quantity(),
                h.holdingName(), h.holdingValue(), h.totalCost(), h.avgPrice(), h.sortOrder());
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
        /** 예수금·현금 잔액 (투자 자산의 매수 대기 자금). balance = cashBalance + holdingBalance */
        Long cashBalance,
        /** 보유 종목 평가금액. 보유가 없으면 0. */
        Long holdingBalance,
        
        String currency,
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. */
        java.math.BigDecimal exchangeRate,
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
                info.balance(), info.cashBalance(), info.holdingBalance(),
                info.currency(), info.exchangeRate(), info.color(),
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
        /** 이자 (대출 상환 시). amount 중 이 금액은 부채를 줄이지 않고 지출로 잡힌다. */
        Long interestAmount,
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
        /** 이자 (대출 상환 시). */
        Long interestAmount,
        /** 원금 = amount − interestAmount. */
        Long principalAmount,
        String description,
        /** 시스템이 만든 이체의 출처 (TRADE_SETTLEMENT). null 이면 사용자가 만든 이체 — 값이 있으면 수정·삭제가 잠긴다. */
        String autoSource,
        LocalDateTime transferDate,
        LocalDateTime createAt
    ) {
        public static TransferResponse from(AssetServiceDto.TransferInfo info) {
            return new TransferResponse(
                info.rowId(), info.userRowId(),
                info.fromAssetRowId(), info.fromAssetName(),
                info.toAssetRowId(), info.toAssetName(),
                info.amount(), info.fee(), info.interestAmount(), info.principalAmount(), info.description(),
                info.autoSource(),
                info.transferDate(), info.createAt()
            );
        }
    }

    public record TransferListResponse(List<TransferResponse> transfers) {
        public static TransferListResponse from(List<AssetServiceDto.TransferInfo> infos) {
            return new TransferListResponse(infos.stream().map(TransferResponse::from).toList());
        }
    }

    /** 잔액 재산정 결과 — 다시 계산한 자산 수. */
}
