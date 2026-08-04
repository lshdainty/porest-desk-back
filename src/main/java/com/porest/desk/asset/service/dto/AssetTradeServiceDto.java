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
        String description,
        /**
         * 결제 계좌 — 지정하면 증권계좌 예수금 대신 이 계좌에서 나가고 들어온다.
         *
         * <p>예수금을 따로 관리하지 않는 사용자를 위한 길이다. 증권계좌에 돈을 옮겨 두는
         * 이체를 먼저 적지 않아도 매수 한 번으로 끝난다. 통장이 마이너스가 되는 건 막지 않는다 —
         * 초기 잔액을 안 채우고 쓰는 가계부에선 정상적인 상태다.
         */
        Long settlementAssetRowId
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
        String description,
        Long settlementAssetRowId
    ) {
        public static TradeInfo from(AssetTrade t) {
            return new TradeInfo(
                t.getRowId(), t.getAsset().getRowId(), t.getTradeType(), t.getHoldingType(),
                t.getHoldingKey(), t.getLinked() == com.porest.core.type.YNType.Y,
                t.getQuantity(), t.getAmount(), t.getFee(), t.getRealizedPl(),
                t.getTradeDate(), t.getDescription(),
                t.getSettlementAsset() != null ? t.getSettlementAsset().getRowId() : null);
        }
    }
}
