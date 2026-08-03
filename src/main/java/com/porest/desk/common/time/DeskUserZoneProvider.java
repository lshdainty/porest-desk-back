package com.porest.desk.common.time;

import com.porest.core.time.UserZoneProvider;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * desk 의 사용자 타임존 조회 — {@code users.timezone}(사용자가 환경설정에서 고른 표시 기준 지역).
 *
 * <p>알 수 없는 사용자·깨진 값이면 {@code null} 을 돌려준다. 서비스 기준으로의 폴백은
 * core {@code UserClock} 이 담당한다 — 여기서 UTC 를 돌려주면 폴백 기회를 뺏는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeskUserZoneProvider implements UserZoneProvider {

    private final UserRepository userRepository;

    @Override
    public ZoneId zoneOf(Long userRowId) {
        if (userRowId == null) {
            return null;
        }
        return userRepository.findById(userRowId)
            .map(User::getTimezone)
            .map(this::parseOrNull)
            .orElse(null);
    }

    private ZoneId parseOrNull(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            // 저장된 값이 깨져도 화면이 죽으면 안 된다 — 서비스 기준 폴백에 맡긴다.
            log.warn("알 수 없는 사용자 타임존 '{}' — 서비스 기준으로 폴백", timezone);
            return null;
        }
    }
}
