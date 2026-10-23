package io.groo.damda.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 시세 상세 — {@code Output_0}(객체) 하나에 더해 <b>뒤쪽 객체 블록 하나</b>를 같이 읽는 봉투.
 * 국내 현재가는 {@code Output_2}(VI 발동 칸), ETF 현재가는 {@code Output_3}(NAV) 에 우리가 쓰는
 * 칸이 있다. {@code Output_1}(시간대별 체결 목록)은 읽지 않는다 — 선언하지 않으니 객체로 오든
 * 배열로 오든 역직렬화가 성공한다.
 *
 * <p>{@code Output_2} · {@code Output_3} 를 둘 다 선언해 두고 쓰는 쪽만 읽는다 — 봉투를 하나로
 * 두려는 것이고, 나머지는 null 로 남는다.
 *
 * @param <P> {@code Output_0} 형태
 * @param <V> {@code Output_2} 형태
 * @param <N> {@code Output_3} 형태
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamuQuoteEnvelope<P, V, N>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") P output0,
        @JsonProperty("Output_2") V output2,
        @JsonProperty("Output_3") N output3,
        @JsonProperty("message") NamuMessage message
) implements NamuResponse {

    @Override
    public boolean hasPayload() {
        return output0 != null;
    }
}
