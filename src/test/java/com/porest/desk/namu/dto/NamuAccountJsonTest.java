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
    }

    @Test
    @DisplayName("우리 응답은 accountNo 로 나간다 — 클라이언트가 읽는 이름이다")
    void writesOurFieldNames() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(
            new NamuAccountDto.Account("12345678-01", "01")));

        assertThat(node.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("accountNo", "accountType");
    }
}
