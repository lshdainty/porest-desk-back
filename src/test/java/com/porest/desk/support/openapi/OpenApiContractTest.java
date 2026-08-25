package com.porest.desk.support.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 스펙(<code>/api-docs</code>)이 <b>실제로 나가는 것</b>과 같은 말을 하는지.
 *
 * <p>이 레포엔 코드젠도 공유 타입도 없어서 front·app 은 이 문서를 읽고 손으로 짠다. 문서가
 * 틀리면 컴파일도 테스트도 안 깨지고 <b>클라이언트만 조용히 틀린다</b> — 그래서 문서 쪽을
 * 여기서 붙잡아 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiContractTest {

    @LocalServerPort int port;

    private JsonNode doc;

    @BeforeEach
    void fetch() throws Exception {
        doc = new ObjectMapper().readTree(RestClient.create().get()
            .uri("http://localhost:" + port + "/api-docs")
            .retrieve().body(String.class));
    }

    private JsonNode schema(String name) {
        JsonNode s = doc.path("components").path("schemas").path(name);
        assertThat(s.isMissingNode()).as("스키마 %s 가 스펙에 없다", name).isFalse();
        return s;
    }

    private JsonNode props(String name) {
        return schema(name).path("properties");
    }

    @Nested
    @DisplayName("증권사 크리덴셜 — 신규와 레거시가 다른 본문을 쓴다")
    class CredentialBodies {

        @Test
        @DisplayName("신규 등록은 apiKey/apiSecret 로 문서화된다")
        void securitiesBody() {
            assertThat(props("SecuritiesCredentialRegisterRequest").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("apiKey", "apiSecret");
        }

        @Test
        @DisplayName("레거시 토스 등록은 clientId/clientSecret 그대로다")
        void legacyBody() {
            assertThat(props("TossCredentialRegisterRequest").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("clientId", "clientSecret");
        }

        @Test
        @DisplayName("두 경로가 서로 다른 스키마를 가리킨다 — 같은 $ref 를 쓰면 한쪽이 덮인다")
        void pathsPointAtDifferentSchemas() {
            assertThat(requestRef("/api/v1/users/me/securities-credentials/{broker}"))
                .isNotEqualTo(requestRef("/api/v1/users/me/toss-credential"));
        }

        private String requestRef(String path) {
            return doc.path("paths").path(path).path("post").path("requestBody")
                .path("content").path("application/json").path("schema").path("$ref").asText();
        }
    }

    @Nested
    @DisplayName("환율·계좌 — 증권사별 응답이 안 섞인다")
    class BrokerSpecificSchemas {

        @Test
        @DisplayName("증권사 무관 환율은 base/quote/rate 다")
        void securitiesExchangeRate() {
            assertThat(props("SecuritiesExchangeRateResponse").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("base", "quote", "rate");
        }

        @Test
        @DisplayName("토스 환율은 별도 스키마로 남는다")
        void tossExchangeRate() {
            assertThat(props("TossExchangeRateResponse").has("baseCurrency")).isTrue();
        }

        @Test
        @DisplayName("나무 계좌와 토스 계좌가 갈라져 있다")
        void accounts() {
            assertThat(props("NamuAccount").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("accountNo", "accountType");
            assertThat(props("TossAccount").has("accountSeq")).isTrue();
        }
    }

    @Nested
    @DisplayName("값의 모양")
    class ValueShapes {

        @Test
        @DisplayName("현재가 응답에 내부 판별용 krw 가 없다")
        void priceQuoteHasNoKrwFlag() {
            assertThat(props("PriceQuote").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("symbol", "price", "currency", "previousClose");
        }

        @Test
        @DisplayName("수량·평단가는 문자열로 문서화된다 — 실제로 문자열이 나간다")
        void decimalsAreDocumentedAsString() {
            assertThat(props("HoldingResponse").path("quantity").path("type").asText())
                .isEqualTo("string");
            assertThat(props("HoldingResponse").path("avgPrice").path("type").asText())
                .isEqualTo("string");
            assertThat(props("TradeResponse").path("quantity").path("type").asText())
                .isEqualTo("string");
        }

        @Test
        @DisplayName("나무 국내시세의 marketCode 는 나무 어휘만 받는다고 밝힌다")
        void namuMarketCodeIsEnumerated() {
            JsonNode param = doc.path("paths").path("/api/v1/namu/kr/price").path("get")
                .path("parameters");
            JsonNode marketCode = null;
            for (JsonNode p : param) {
                if ("marketCode".equals(p.path("name").asText())) {
                    marketCode = p;
                }
            }
            assertThat(marketCode).as("marketCode 파라미터가 스펙에 없다").isNotNull();
            assertThat(marketCode.path("schema").path("enum")).isNotEmpty();
            assertThat(marketCode.path("schema").path("enum").toString())
                .contains("KRX").contains("NXT").contains("UNT").doesNotContain("KOSPI");
        }
    }
}
