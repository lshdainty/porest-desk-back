package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 목록({@code Output_1})만 읽고 {@code Output_0} 은 <b>아예 바인딩하지 않는</b> 봉투.
 * 기간별시세(캔들) 계열이 이 모양이다.
 *
 * <h2>왜 봉투가 하나 더 필요한가</h2>
 *
 * <p>NH 스펙은 기간별시세 두 API 의 {@code Output_0} 에 <b>⚠️ 경고</b>를 달아 뒀다 —
 * "명세상 Array 로 선언되어 있으나 예시 응답은 Object 입니다. 실제 응답 타입을 확인 후
 * 사용하세요." 이 레포는 정확히 그 자리에서 한 번 크게 당했다(#254): {@code Output_0} 을
 * 배열로 못박았다가 나무 조회가 통째로 실패했다. Jackson 이
 * {@code MismatchedInputException} 을 던지고 RestTemplate 이 그걸 감싸 "증권사 API 오류" 로
 * 둔갑시키기 때문에, 원인이 봉투 모양이라는 것도 한참 뒤에 알았다.
 *
 * <p><b>그래서 이번엔 맞히지 않는다.</b> 캔들은 스펙이 {@code Output_1} 이라고 못박은 자리에
 * 있고 우리에게 필요한 것도 그것뿐이다. {@code Output_0}(종목 요약·현재가)은
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} 로 <b>읽지 않고 흘려보낸다</b> —
 * 필드를 선언하지 않으니 객체로 오든 배열로 오든 역직렬화가 성공한다.
 * 실제 타입이 무엇으로 밝혀지든 이 코드는 고칠 게 없다.
 *
 * <p>({@code JsonNode} 로 받는 방법도 있는데, 그건 Jackson 2 타입이라
 * Spring Boot 4 의 Jackson 3 컨버터가 못 읽는다 — 실제로 이 레포 테스트에서 그렇게 터졌다.
 * 선언을 지우는 쪽이 라이브러리 버전과도 무관하다.)
 *
 * @param output1 봉 목록. 성공이어도 없을 수 있다 — 나무는 "데이터가 있을 때만" 블록을 내려준다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamuCandleEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_1") List<T> output1
) implements NamuResponse {

    public List<T> items() {
        return output1 == null ? List.of() : output1;
    }
}
