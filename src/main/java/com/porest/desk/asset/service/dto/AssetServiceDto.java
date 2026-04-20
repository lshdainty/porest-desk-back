package com.porest.desk.asset.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
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
        String icon,
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        Long cardCatalogRowId
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
        YNType isIncludedInTotal,
        Long cardCatalogRowId
    ) {}

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
        String currency,
        String icon,
        String color,
        String institution,
        String memo,
        Integer sortOrder,
        YNType isIncludedInTotal,
        CardCatalogBrief cardCatalog,
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
                CardCatalogBrief.from(asset.getCardCatalog()),
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
