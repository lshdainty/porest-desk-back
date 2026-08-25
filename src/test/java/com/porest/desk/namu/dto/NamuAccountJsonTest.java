package com.porest.desk.namu.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NamuAccountDto.Account} 는 <b>들어올 때와 나갈 때 이름이 다르다.</b>
 *
 * <p>나무가 주는 {@code acct_no} 를 읽어야 하고, 우리 API({@code GET /api/v1/namu/accounts})는
 * front·app 이 읽는 {@code accountNo} 로 내보내야 한다. 양방향인 {@code @JsonProperty} 로는
 * 둘 중 하나만 되므로 읽기 전용 별칭을 쓴다 — 자바 객체를 비교해서는 못 잡는 자리라 JSON 을 본다.
 */
class NamuAccountJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("나무 응답의 acct_no 를 읽는다")
    void readsNamuFieldNames() throws Exception {
        NamuAccountDto.Account account = mapper.readValue(
            "{\"acct_no\":\"12345678-01\",\"acct_type\":\"01\",\"unknown\":\"x\"}",
            NamuAccountDto.Account.class);

        assertThat(account.accountNo()).isEqualTo("12345678-01");
        assertThat(account.accountType()).isEqualTo("01");
        // usable 은 나무가 주는 값이 아니라 서비스가 환경을 보고 채운다.
        // 원시 boolean 이면 여기서 "Cannot map null into boolean" 으로 목록 파싱이 통째로 깨진다.
        assertThat(account.usable()).isNull();
    }

    @Test
    @DisplayName("usable 은 나가는 쪽에만 있다 — 화면이 어느 계좌를 고를지 여기서 안다")
    void usableIsAnOutboundOnlyFlag() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(
            NamuAccountDto.Account.of("12345678-01", "01").withUsable(true)));

        assertThat(node.get("usable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("우리 응답은 accountNo 로 나간다 — 클라이언트가 읽는 이름이다")
    void writesOurFieldNames() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(
            NamuAccountDto.Account.of("12345678-01", "01")));

        assertThat(node.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("accountNo", "accountType", "usable");
    }
}
