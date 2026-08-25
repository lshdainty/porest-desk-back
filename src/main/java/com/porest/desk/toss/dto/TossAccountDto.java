package com.porest.desk.toss.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스증권 Account(계좌) · Asset(보유 자산) 응답 DTO 모음.<br>
 * 금액은 정밀도 보존을 위해 String, 통화별 합산은 {@link Price}(krw/usd) 구조를 따른다.
 */
public final class TossAccountDto {

    private TossAccountDto() {
    }

    /** 계좌 정보. accountSeq 는 보유주식/주문 등 계좌 귀속 API 호출 시 식별 키로 사용 */
    @Schema(name = "TossAccount")
    public record Account(
            String accountNo,
            Long accountSeq,
            String accountType
    ) {
    }

    /** 통화별 금액 (원화 필수, 외화 nullable) */
    public record Price(
            String krw,
            String usd
    ) {
    }

    // === 보유 주식 (Holdings) ===

    /** 보유 자산 현황 응답 (전체 합산 요약 + 종목별 목록) */
    public record HoldingsOverview(
            Price totalPurchaseAmount,
            OverviewMarketValue marketValue,
            OverviewProfitLoss profitLoss,
            OverviewDailyProfitLoss dailyProfitLoss,
            List<HoldingsItem> items
    ) {
    }

    /** 전체 평가금액 요약 */
    public record OverviewMarketValue(
            Price amount,
            Price amountAfterCost
    ) {
    }

    /** 전체 손익 요약 */
    public record OverviewProfitLoss(
            Price amount,
            Price amountAfterCost,
            String rate,
            String rateAfterCost
    ) {
    }

    /** 전체 일간 손익 요약 */
    public record OverviewDailyProfitLoss(
            Price amount,
            String rate
    ) {
    }

    /** 보유 종목 한 건 */
    public record HoldingsItem(
            String symbol,
            String name,
            String marketCountry,
            String currency,
            String quantity,
            String lastPrice,
            String averagePurchasePrice,
            MarketValue marketValue,
            ProfitLoss profitLoss,
            DailyProfitLoss dailyProfitLoss,
            Cost cost
    ) {
    }

    /** 종목별 평가금액 */
    public record MarketValue(
            String purchaseAmount,
            String amount,
            String amountAfterCost
    ) {
    }

    /** 종목별 손익 */
    public record ProfitLoss(
            String amount,
            String amountAfterCost,
            String rate,
            String rateAfterCost
    ) {
    }

    /** 종목별 일간 손익 */
    public record DailyProfitLoss(
            String amount,
            String rate
    ) {
    }

    /** 종목별 비용 (수수료/세금) */
    public record Cost(
            String commission,
            String tax
    ) {
    }
}
