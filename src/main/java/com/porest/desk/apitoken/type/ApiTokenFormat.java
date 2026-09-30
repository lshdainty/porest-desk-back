package com.porest.desk.apitoken.type;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 프로그램용 API 토큰의 모양 — 만들고, 알아보고, 해시한다.
 *
 * <p>모양은 {@code pdk_} + 무작위 32바이트(Base64URL, 패딩 없음 43자) = 47자다.
 *
 * <p><b>접두사를 두는 이유</b> — 같은 {@code Authorization: Bearer} 자리에 desk JWT 도 온다.
 * 접두사가 있으면 두 필터가 값을 파싱해 보지 않고도 제 것인지 가른다. JWT 필터가 이 값을 JWT 로
 * 읽으려 들면 요청마다 "JWT invalid" 경고가 두 줄씩 남는다. 로그·설정 파일에 섞여 들어갔을 때
 * 사람이 알아보기에도 좋다.
 *
 * <p><b>저장은 해시로만 한다</b> — 원문은 발급 응답에서 한 번 보여 주고 끝이다. DB 가 새도
 * 토큰으로 쓸 수 없다. 무작위 256비트라 솔트·느린 해시가 필요 없다(사전 공격이 성립하지 않는다).
 */
public final class ApiTokenFormat {

    public static final String PREFIX = "pdk_";

    private static final int RANDOM_BYTES = 32;

    /** 목록에서 "어느 토큰인지" 알아보게 남기는 앞부분 길이 — 접두사 + 8자. */
    private static final int DISPLAY_LENGTH = PREFIX.length() + 8;

    private ApiTokenFormat() {
    }

    public static String generate(SecureRandom random) {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 이 값이 API 토큰 모양인지. 유효한지는 모른다 — 그건 DB 가 정한다. */
    public static boolean looksLike(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** SHA-256 16진수 64자. DB 에는 이것만 남는다. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM 이 SHA-256 을 갖고 있어야 한다(JCA 필수 알고리즘) — 여기 오면 런타임이 망가진 것이다.
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    public static String displayPrefix(String token) {
        return token.substring(0, Math.min(DISPLAY_LENGTH, token.length()));
    }
}
