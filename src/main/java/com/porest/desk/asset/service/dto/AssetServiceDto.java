package com.porest.desk.asset.service.dto;

import java.math.BigDecimal;
import com.porest.desk.asset.type.HoldingType;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardCatalog;

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
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. null 이면 기존 값 유지·신규는 1. */
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
        // 투자 보유 목록 (INVESTMENT 전용). null=없음.
        List<HoldingCommand> holdings
    ) {}

    public record UpdateAssetCommand(
        String assetName,
        AssetType assetType,
        Long balance,
        String currency,
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. null 이면 기존 값 유지·신규는 1. */
        java.math.BigDecimal exchangeRate,
        String color,
        String institution,
        String memo,
        YNType isIncludedInTotal,
        Long cardCatalogRowId,
        Long creditLimit,
        Integer paymentDay,
        Long paymentAssetRowId,
        // 투자 보유 목록 — null=무변경, 리스트=전체 교체(빈 리스트=전부 삭제).
        List<HoldingCommand> holdings
    ) {}

    /**
     * 투자 보유 입력 — linked=true: symbol+quantity 필수 / false: holdingName+holdingValue 필수(quantity 선택).
     * holdingType 미지정은 STOCK 으로 본다(구버전 클라이언트 하위호환).
     */
    public record HoldingCommand(
        /**
         * 기존 보유 행 아이디 — 있으면 제자리 수정, 없으면 신규.
         *
         * <p>이게 없으면 편집할 때마다 보유를 통째로 지우고 새로 만들게 되고, row_id 가
         * 매번 바뀌어 거래(asset_trade)가 이름으로 묶일 수밖에 없다. 그러면 종목명을
         * 바꾸는 순간 원가와 매매 이력이 끊긴다.
         */
        Long rowId,
        HoldingType holdingType,
        Boolean linked,
        /**
         * stock_master 기준 시장코드 — 선택. 안 보내오면 서버가 심볼로 1회 해석하고,
         * 여러 시장에 걸리면 비워 둔다(구버전 클라이언트는 안 보낸다).
         */
        String marketCode,
        String symbol,
        BigDecimal quantity,
        String holdingName,
        Long holdingValue,
        /** 총 매수원가 (원화). null 이면 기존 값 유지 — 실현손익 계산의 기준이다. */
        Long totalCost
    ) {}

    public record HoldingInfo(
        Long rowId,
        HoldingType holdingType,
        boolean linked,
        /** 저장된 시장코드. 확정하지 못한 행은 null 이다. */
        String marketCode,
        String symbol,
        BigDecimal quantity,
        String holdingName,
        Long holdingValue,
        /** 총 매수원가 (원화, 수수료 포함). 평가액과의 차이가 평가손익이다. */
        Long totalCost,
        /** 평단가 — 총원가 / 수량. 수량이 없으면 null. */
        BigDecimal avgPrice,
        Integer sortOrder
    ) {
        public static HoldingInfo from(AssetHolding h) {
            return new HoldingInfo(
                h.getRowId(), h.getHoldingType(), h.isLinked(), h.getMarketCode(), h.getSymbol(),
                h.getQuantity(),
                h.getHoldingName(), h.getHoldingValue(), h.getTotalCost(), h.avgPrice(),
                h.getSortOrder());
        }
    }

    public record CardCatalogBrief(
        Long rowId,
        String cardName,
        String imgUrl,
        String companyName,
        String companyLogoUrl
    ) {
        public static CardCatalogBrief from(CardCatalog c) {
            if (c == null) return null;
            String companyName = null;
            String companyLogoUrl = null;
            if (c.getCompany() != null) {
                companyName = c.getCompany().getName();
                companyLogoUrl = c.getCompany().getLogoUrl();
            }
            return new CardCatalogBrief(c.getRowId(), c.getCardName(), c.getImgUrl(), companyName, companyLogoUrl);
        }
    }

    public record AssetInfo(
        Long rowId,
        Long userRowId,
        String assetName,
        AssetType assetType,
        Long balance,
        /** 예수금·현금 잔액. 투자 자산의 매수 대기 자금이 여기 잡힌다. balance = cashBalance + holdingBalance */
        Long cashBalance,
        /** 보유 종목 평가금액. 보유가 없으면 0. */
        Long holdingBalance,
        
        String currency,
        /** 원화 환산율 (통화 1단위당 원화). KRW 는 1. null 이면 기존 값 유지·신규는 1. */
        java.math.BigDecimal exchangeRate,
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        YNType isIncludedInTotal,
        CardCatalogBrief cardCatalog,
        Long creditLimit,
        Integer paymentDay,
        Long paymentAssetRowId,
        /** 레거시 단일 연동의 시장코드. 확정하지 못했으면 null. */
        String marketCode,
        String symbol,
        Long quantity,
        // 투자 보유 목록 (INVESTMENT 외/보유 없음 = 빈 리스트)
        List<HoldingInfo> holdings,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        /**
         * 체크카드의 이번 달(1일~) 사용 합계 — 체크카드만 값, 그 외 null.
         * 결제가 연결 계좌에서 즉시 빠져 balance 가 항상 0 이라, 이 값이 없으면
         * 화면에서 "이 카드로 얼마 썼는지" 를 알 길이 없다. 순자산 계산과는 무관하다
         * (돈은 이미 계좌에서 빠졌다 — balance 에 실으면 이중 차감이 된다).
         */
        Long monthlyUsedAmount
    ) {
        /** 잔액 없이(0) 만든다 — 잔액이 화면에 안 쓰이는 응답 전용. */
        public static AssetInfo from(Asset asset) {
            return from(asset, List.of(), null);
        }

        /** 잔액 없이(0) 만든다 — 호출부가 집계 결과를 따로 붙일 때. */
        public static AssetInfo from(Asset asset, List<HoldingInfo> holdings) {
            return from(asset, holdings, null);
        }

        /**
         * @param split 이력에서 집계한 채널별 잔액. 자산에 잔액 캐시 컬럼은 없다 —
         *             금액은 언제나 이력이 진실이고, 캐시를 두면 어긋난 값으로 판단하게 된다.
         */
        public static AssetInfo from(Asset asset, List<HoldingInfo> holdings,
                                     AssetBalanceHistoryService.Split split) {
            AssetBalanceHistoryService.Split s =
                split != null ? split : AssetBalanceHistoryService.Split.ZERO;
            long balance = s.total();
            long cash = s.cash();
            long holdingValue = s.holding();
            return new AssetInfo(
                asset.getRowId(),
                asset.getUser().getRowId(),
                asset.getAssetName(),
                asset.getAssetType(),
                balance,
                cash,
                holdingValue,
                asset.getCurrency(),
                asset.getExchangeRate(),
                asset.getColor(),
                asset.getInstitution(),
                asset.getMemo(),
                asset.getSortOrder(),
                asset.getIsIncludedInTotal(),
                CardCatalogBrief.from(asset.getCardCatalog()),
                asset.getCreditLimit(),
                asset.getPaymentDay(),
                asset.getPaymentAsset() != null ? asset.getPaymentAsset().getRowId() : null,
                asset.getMarketCode(),
                asset.getSymbol(),
                asset.getQuantity(),
                holdings != null ? holdings : List.of(),
                asset.getCreateAt(),
                asset.getModifyAt(),
                null
            );
        }

        public AssetInfo withMonthlyUsedAmount(Long monthlyUsed) {
            return new AssetInfo(rowId, userRowId, assetName, assetType, balance, cashBalance,
                holdingBalance, currency, exchangeRate, color, institution, memo, sortOrder,
                isIncludedInTotal, cardCatalog, creditLimit, paymentDay, paymentAssetRowId,
                marketCode, symbol, quantity, holdings, createAt, modifyAt, monthlyUsed);
        }
    }

    public record ReorderItem(
        Long assetId,
        Integer sortOrder
    ) {}

    public record AssetSummary(
        Long totalBalance,          // 기존 호환: 모든 자산 balance 합 (부채도 양수로 포함)
        Long totalAssets,           // 순수 자산 합 (BANK_ACCOUNT, CASH, SAVINGS, INVESTMENT, CHECK_CARD)
        Long totalDebt,             // 부채 합 (CREDIT_CARD, LOAN) — 양수
        Long netWorth,              // totalAssets - totalDebt
        Long lastMonthNetWorth,     // 이번 달 순수입을 역산해 추정한 지난달 말 순자산
        Long changeAmount,          // netWorth - lastMonthNetWorth (= 이번 달 수입 - 이번 달 지출)
        Double changePercent,       // changeAmount / |lastMonthNetWorth| * 100 (소수 1자리). lastMonth==0이면 0.0
        List<AssetTypeSummary> byType
    ) {}

    public record AssetTypeSummary(
        AssetType assetType,
        Long totalBalance,
        Integer count
    ) {}

    public record NetWorthTrendPoint(
        Integer year,
        Integer month,
        Long netWorth
    ) {}

    /** 자산 상세 차트용 — 각 주의 월요일(weekStart) 기준 잔액. */
    public record AssetBalancePoint(
        LocalDate weekStart,
        Long balance
    ) {}

    // === Asset Transfer ===
    public record CreateTransferCommand(
        Long userRowId,
        Long fromAssetRowId,
        Long toAssetRowId,
        Long amount,
        Long fee,
        /** 이자 (대출 상환 시). amount 중 이 금액은 부채를 줄이지 않고 지출로 잡힌다. */
        Long interestAmount,
        String description,
        LocalDateTime transferDate,
        /**
         * 시스템이 만드는 이체면 그 출처 (TRADE_SETTLEMENT). 사용자가 만드는 이체는 null.
         * 값이 있으면 이후 수정·삭제가 잠긴다.
         */
        String autoSource
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
        /** 이자 (대출 상환 시). */
        Long interestAmount,
        /** 원금 = amount − interestAmount. 입금 자산(대출)에 실제로 반영된 금액. */
        Long principalAmount,
        String description,
        /**
         * 시스템이 만든 이체의 출처 (TRADE_SETTLEMENT). null 이면 사용자가 직접 만든 이체.
         * 값이 있으면 수정·삭제가 잠긴다 — 화면이 버튼을 감출 수 있게 내려 준다.
         */
        String autoSource,
        LocalDateTime transferDate,
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
                transfer.getInterestAmount(),
                transfer.principalAmount(),
                transfer.getDescription(),
                transfer.getAutoSource(),
                transfer.getTransferDate(),
                transfer.getCreateAt()
            );
        }
    }
}
