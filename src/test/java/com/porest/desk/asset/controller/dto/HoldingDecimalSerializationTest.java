package com.porest.desk.asset.controller.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수량·평단가는 JSON 에 <b>문자열</b>로 나가야 한다.
 *
 * <p>decimal(28,8) 을 JS number 로 받으면 자릿수가 흔들린다 — 코인 0.00012345 같은 값이 그렇다.
 * 클라이언트도 문자열 그대로 다듬어 표시하도록 짜여 있어서(toLocaleString 은 3자리에서 끊어
 * 0 으로 보여준다), 숫자로 나가면 화면이 깨진다.
 *
 * <p>환율은 다르다 — 표시용 배율이라 숫자로 나가는 게 맞다.
 */
class HoldingDecimalSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("보유 응답 — 수량과 평단가가 문자열로 나간다")
    void holdingQuantityAndAvgPriceAreStrings() throws Exception {
        var res = new AssetApiDto.HoldingResponse(
            1L, HoldingType.CRYPTO, false, null,
            new BigDecimal("0.00012345"), "비트코인", 1_000_000L,
            900_000L, new BigDecimal("7290000000.00000000"), 0);

        String json = mapper.writeValueAsString(res);

        assertThat(json).contains("\"quantity\":\"0.00012345\"");
        assertThat(json).contains("\"avgPrice\":\"7290000000.00000000\"");
    }

    @Test
    @DisplayName("거래 응답 — 수량이 문자열로 나간다")
    void tradeQuantityIsString() throws Exception {
        var res = new AssetTradeApiDto.TradeResponse(
            1L, 11L, TradeType.BUY, HoldingType.GOLD, "금 현물", false,
            new BigDecimal("3.75000000"), 3_000_000L, 0L, null,
            LocalDateTime.of(2026, 8, 3, 10, 0), null, null);

        String json = mapper.writeValueAsString(res);

        assertThat(json).contains("\"quantity\":\"3.75000000\"");
    }
}
