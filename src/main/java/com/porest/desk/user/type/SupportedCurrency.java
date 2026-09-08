package com.porest.desk.user.type;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 화면이 고르게 해 주는 통화 목록 — <b>사용자 기본 통화</b>({@code users.default_currency})가
 * 가질 수 있는 값이다.
 *
 * <p><b>여기서만 좁힌다.</b> {@code asset.currency} · {@code dutch_pay.currency} 는 맨
 * {@code String} 이고 앞으로도 그렇다 — 결제 문자에서 읽은 원화 외 통화
 * ({@code SmsParsed.originalCurrency})처럼 사용자가 고르지 않은 ISO 코드가 그쪽으로 들어온다.
 * 이 enum 은 "서버가 아는 통화 전부" 가 아니라 <b>설정 화면의 선택지</b>다.
 *
 * <p>{@link #PATTERN} 은 요청 DTO 의 {@code @Pattern} 에 쓰려고 문자열로도 둔 것이다. 애노테이션
 * 속성은 컴파일 상수여야 해서 {@code values()} 로 만들 수 없어 손으로 적었고, 둘이 어긋나지
 * 않는지는 {@code SupportedCurrencyTest} 가 잡는다.
 */
public enum SupportedCurrency {
    KRW, USD, EUR, JPY;

    /** {@code @Pattern(regexp = SupportedCurrency.PATTERN)} 용. {@link #values()} 와 같은 목록이다. */
    public static final String PATTERN = "KRW|USD|EUR|JPY";

    /** 기본값 — 컬럼 DEFAULT 와 같다. */
    public static final String DEFAULT = "KRW";

    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(c -> c.name().equals(code));
    }

    /** 테스트가 {@link #PATTERN} 과 맞대 보는 값. */
    public static String joined() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining("|"));
    }
}
