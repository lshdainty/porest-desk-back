package com.porest.desk.securities.type;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * 캔들 한 봉의 주기. <b>증권사 무관 어휘</b>다.
 *
 * <p>값이 {@code 1m}·{@code 1d} 인 이유는 토스 어휘를 그대로 물려받았기 때문이다. 캔들
 * 경로가 {@code /api/v1/toss/candles} 하나뿐이던 시절 프론트가 이 문자열을 쓰기 시작했고,
 * 임베드 차트의 querystring 에도 남아 있다. 여기서 새 어휘를 만들면 <b>같은 뜻의 이름이
 * 둘</b>이 되고 옛 앱이 보내는 값이 갑자기 400 이 된다.
 *
 * <p>증권사별 코드로 바꾸는 일은 각 제공자가 맡는다 — 나무는 국내·해외가
 * <b>같은 주기에 서로 다른 숫자</b>를 쓰기 때문에 여기 한 곳에 담을 수 없다
 * (국내 1=일 / 해외 3=일).
 */
@Getter
@RequiredArgsConstructor
public enum CandleInterval {

    /** 1분봉. 화면의 {@code 1D} 탭이 쓴다. */
    MINUTE_1("1m"),

    /** 일봉. 1주·1개월·3개월·1년 탭이 모두 이걸 쓴다. */
    DAY_1("1d");

    /** 클라이언트가 보내는 값이자 토스 API 가 받는 값. */
    private final String code;

    /**
     * 코드 문자열 → 주기.
     *
     * <p>모르는 값은 {@code SECURITIES_INTERVAL_UNSUPPORTED} 로 거절한다 — 조용히 일봉으로
     * 떨어뜨리면 분봉을 요청한 화면에 일봉이 그려지고, 사용자는 차트가 멈춘 줄 안다.
     */
    public static CandleInterval from(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_INTERVAL_UNSUPPORTED);
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (CandleInterval interval : values()) {
            if (interval.code.equals(normalized)) {
                return interval;
            }
        }
        throw new InvalidValueException(DeskErrorCode.SECURITIES_INTERVAL_UNSUPPORTED);
    }
}
