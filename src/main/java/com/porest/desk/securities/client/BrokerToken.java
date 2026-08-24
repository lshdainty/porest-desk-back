package com.porest.desk.securities.client;

/**
 * 증권사가 발급한 액세스 토큰. 응답 필드명은 회사마다 다르지만(토스 {@code access_token},
 * 나무도 {@code access_token}) 우리가 쓰는 값은 토큰 문자열과 남은 초 둘뿐이라 여기서 통일한다.
 *
 * @param accessToken      {@code Authorization: Bearer} 에 실을 값
 * @param expiresInSeconds 만료까지 남은 초 (나무 86400, 토스는 응답값 그대로)
 */
public record BrokerToken(String accessToken, long expiresInSeconds) {
}
