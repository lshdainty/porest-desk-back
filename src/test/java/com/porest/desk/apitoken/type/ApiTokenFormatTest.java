package com.porest.desk.apitoken.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenFormatTest {

    @Test
    @DisplayName("발급한 토큰은 접두사 + Base64URL 43자다 — 두 번 뽑으면 다른 값이 나온다")
    void generate() {
        SecureRandom random = new SecureRandom();

        String a = ApiTokenFormat.generate(random);
        String b = ApiTokenFormat.generate(random);

        assertThat(a).matches("pdk_[A-Za-z0-9_-]{43}");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("접두사로 제 것인지 가른다 — JWT 는 아니다")
    void looksLike() {
        assertThat(ApiTokenFormat.looksLike("pdk_abc")).isTrue();
        assertThat(ApiTokenFormat.looksLike("eyJhbGciOiJIUzI1NiJ9.e30.sig")).isFalse();
        assertThat(ApiTokenFormat.looksLike("")).isFalse();
        assertThat(ApiTokenFormat.looksLike(null)).isFalse();
    }

    @Test
    @DisplayName("해시는 SHA-256 16진수 64자다 — 같은 입력이면 같은 값이라 DB 에서 찾을 수 있다")
    void hash() {
        // SHA-256("abc") 의 알려진 값(FIPS 180-4 예제)
        assertThat(ApiTokenFormat.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ApiTokenFormat.hash("pdk_x")).hasSize(64).isEqualTo(ApiTokenFormat.hash("pdk_x"));
        assertThat(ApiTokenFormat.hash("pdk_x")).isNotEqualTo(ApiTokenFormat.hash("pdk_y"));
    }

    @Test
    @DisplayName("목록에 보여 줄 앞부분은 접두사 + 8자 — 원문을 되살릴 수 없는 길이다")
    void displayPrefix() {
        String token = ApiTokenFormat.generate(new SecureRandom());

        assertThat(ApiTokenFormat.displayPrefix(token)).hasSize(12).isEqualTo(token.substring(0, 12));
        assertThat(ApiTokenFormat.displayPrefix("pdk_ab")).isEqualTo("pdk_ab");
    }
}
