package com.porest.desk.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 클라이언트가 보낸 일시 문자열 → {@link LocalDateTime}.
 *
 * <p>거래 일시는 <b>사용자가 정한 벽시계</b>다. 타임존을 태우면 자정 근처 거래의 날짜가
 * 하루 밀리므로, 오프셋 없는 로컬 문자열을 그대로 읽는다
 * (클라이언트도 {@code toISOString()} 처럼 UTC 로 바꿔 보내면 안 된다).
 *
 * <p>허용 형식
 * <ul>
 *   <li>{@code yyyy-MM-dd} → 그 날 00:00</li>
 *   <li>{@code yyyy-MM-ddTHH:mm[:ss]}</li>
 *   <li>{@code yyyy-MM-dd HH:mm[:ss]} — 공백 구분자</li>
 * </ul>
 */
public final class WallClockDateTimeParser {

    private WallClockDateTimeParser() {
    }

    /** null·빈 문자열이면 null. 형식이 어긋나면 {@link java.time.format.DateTimeParseException}. */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed.replace(' ', 'T'));
    }
}
