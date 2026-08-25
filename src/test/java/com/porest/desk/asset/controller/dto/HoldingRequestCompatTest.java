package com.porest.desk.asset.controller.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.desk.asset.type.HoldingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시장코드는 <b>선택</b>이다 — 안 보내는 구버전 앱이 계속 저장돼야 한다.
 *
 * <p>운영에 옛 앱(v1.12.0)이 돌고 있고 그 앱은 {@code marketCode} 라는 필드를 모른다.
 * 필수로 만들면 그 사용자들의 자산 저장이 그날로 깨진다. 여기서 "없어도 파싱된다" 를 고정한다.
 */
class HoldingRequestCompatTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("marketCode 없는 구버전 페이로드도 그대로 파싱된다 — 시장코드만 null")
    void oldPayloadWithoutMarketCode() throws Exception {
        String json = """
            {"rowId":7,"holdingType":"STOCK","linked":true,"tossSymbol":"005930",
             "quantity":"10","holdingValue":null,"totalCost":700000}
            """;

        AssetApiDto.HoldingRequest req = mapper.readValue(json, AssetApiDto.HoldingRequest.class);

        assertThat(req.marketCode()).isNull();
        assertThat(req.tossSymbol()).isEqualTo("005930");
        assertThat(req.quantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(req.toCommand().marketCode()).isNull();
        assertThat(req.toCommand().symbol()).isEqualTo("005930");
        assertThat(req.toCommand().totalCost()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("새 앱이 보낸 marketCode 는 커맨드까지 그대로 간다")
    void newPayloadCarriesMarketCode() throws Exception {
        String json = """
            {"holdingType":"STOCK","linked":true,"marketCode":"NAS","tossSymbol":"SPY","quantity":"3"}
            """;

        AssetApiDto.HoldingRequest req = mapper.readValue(json, AssetApiDto.HoldingRequest.class);

        assertThat(req.toCommand().marketCode()).isEqualTo("NAS");
        assertThat(req.toCommand().holdingType()).isEqualTo(HoldingType.STOCK);
    }

    @Test
    @DisplayName("연결 요청도 시장코드 없이 파싱된다")
    void tossLinkWithoutMarketCode() throws Exception {
        AssetApiDto.TossLinkRequest req = mapper.readValue(
            "{\"symbol\":\"005930\",\"quantity\":10}", AssetApiDto.TossLinkRequest.class);

        assertThat(req.marketCode()).isNull();
        assertThat(req.symbol()).isEqualTo("005930");
        assertThat(req.quantity()).isEqualTo(10L);
    }
}
