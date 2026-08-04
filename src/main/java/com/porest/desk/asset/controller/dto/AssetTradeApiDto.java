package com.porest.desk.asset.controller.dto;

import com.porest.desk.asset.service.dto.AssetTradeServiceDto;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AssetTradeApiDto {
    private AssetTradeApiDto() {}

    /**
     * 매수·매도 입력.
     *
     * <p>{@code amount} 는 수수료를 뺀 순수 거래대금이다. 수수료는 매수면 취득원가에 들어가고
     * 매도면 대금에서 빠진다 — 어느 쪽이든 예수금에서 실제로 나간다.
     */
    public record CreateTradeRequest(
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

    public record TradeResponse(
        Long rowId,
        Long assetRowId,
        TradeType tradeType,
        HoldingType holdingType,
        String holdingKey,
        boolean linked,
        /** 소수 정밀도 보존 — 금 3.75g·코인 0.05 개가 숫자로 나가면 자릿수가 흔들린다. */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        BigDecimal quantity,
        Long amount,
        Long fee,
        /** 실현손익 (매도 전용). 이익 양수 / 손실 음수. */
        Long realizedPl,
        LocalDateTime tradeDate,
        String description
    ) {
        public static TradeResponse from(AssetTradeServiceDto.TradeInfo t) {
            return new TradeResponse(t.rowId(), t.assetRowId(), t.tradeType(), t.holdingType(),
                t.holdingKey(), t.linked(), t.quantity(), t.amount(), t.fee(),
                t.realizedPl(), t.tradeDate(), t.description());
        }

        public static List<TradeResponse> from(List<AssetTradeServiceDto.TradeInfo> list) {
            return list.stream().map(TradeResponse::from).toList();
        }
    }
}
