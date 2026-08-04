package com.porest.desk.asset.service.dto;

import com.porest.desk.asset.domain.AssetTrade;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AssetTradeServiceDto {
    private AssetTradeServiceDto() {}

    /**
     * 매수·매도 입력.
     *
     * <p>{@code amount} 는 수수료를 뺀 순수 거래대금이다. 수수료는 매수면 원가에 더해지고
     * 매도면 대금에서 빠진다 — 어느 쪽이든 예수금에서 실제로 나간다.
     */
    public record CreateTradeCommand(
        Long userRowId,
        Long assetRowId,
        TradeType tradeType,
        HoldingType holdingType,
        /** 종목 식별자 — 연동은 토스 종목코드, 미연동은 항목명. */
        String holdingKey,
        Boolean linked,
        BigDecimal quantity,
        Long amount,
        Long fee,
        LocalDateTime tradeDate,
        String description
    ) {}

    public record TradeInfo(
        Long rowId,
        Long assetRowId,
        TradeType tradeType,
        HoldingType holdingType,
        String holdingKey,
        boolean linked,
        BigDecimal quantity,
        Long amount,
        Long fee,
        Long realizedPl,
        LocalDateTime tradeDate,
        String description
    ) {
        public static TradeInfo from(AssetTrade t) {
            return new TradeInfo(
                t.getRowId(), t.getAsset().getRowId(), t.getTradeType(), t.getHoldingType(),
                t.getHoldingKey(), t.getLinked() == com.porest.core.type.YNType.Y,
                t.getQuantity(), t.getAmount(), t.getFee(), t.getRealizedPl(),
                t.getTradeDate(), t.getDescription());
        }
    }
}
