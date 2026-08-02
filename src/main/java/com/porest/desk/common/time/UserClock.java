package com.porest.desk.common.time;

import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 사용자 기준 시각.
 *
 * <p>서버·DB 는 UTC 로 두고(로그·시점 비교의 표준), 사용자에게 보이는 "오늘"·"지금" 만
 * 사용자 타임존({@code users.timezone})으로 판단한다.
 *
 * <p><b>왜 필요한가</b> — 컨테이너에 TZ 가 없으면 JVM 기본이 UTC 다. 그대로
 * {@code LocalDate.now()} 를 쓰면 한국 사용자에게는 오전 9시 전까지 "오늘" 이 하루 전으로 잡힌다.
 *
 * <p><b>벽시계 컬럼과의 정합</b> — {@code expense_date}·{@code transfer_date}·
 * {@code asset_balance_history.effective_at} 는 타임존 없는 naive 값이고, 클라이언트가 보내는 값은
 * 사용자 로컬 벽시계다. 서버가 같은 컬럼에 UTC 로 찍으면 한 컬럼에 두 기준이 섞여 정렬이 깨진다
 * (실제로 잔액 앵커가 거래보다 뒤로 밀려 이체가 사라지는 문제가 있었다).
 * 그래서 그 컬럼들에 넣을 값은 반드시 이 클래스로 만든다.
 *
 * <p>배치처럼 특정 사용자에 속하지 않는 처리는 {@link ServiceClock} 을 쓴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserClock {

    private final UserRepository userRepository;
    private final ServiceClock serviceClock;

    /** 사용자 타임존. 값이 없거나 알 수 없는 ID 면 서비스 기준으로 폴백. */
    public ZoneId zoneOf(User user) {
        if (user == null) {
            return serviceClock.zone();
        }
        return parse(user.getTimezone());
    }

    public ZoneId zoneOf(Long userRowId) {
        if (userRowId == null) {
            return serviceClock.zone();
        }
        return userRepository.findById(userRowId)
            .map(u -> parse(u.getTimezone()))
            .orElseGet(serviceClock::zone);
    }

    public LocalDate today(Long userRowId) {
        return LocalDate.now(zoneOf(userRowId));
    }

    public LocalDate today(User user) {
        return LocalDate.now(zoneOf(user));
    }

    public LocalDateTime now(Long userRowId) {
        return LocalDateTime.now(zoneOf(userRowId));
    }

    public LocalDateTime now(User user) {
        return LocalDateTime.now(zoneOf(user));
    }

    private ZoneId parse(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return serviceClock.zone();
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            // 저장된 값이 깨져도 화면이 죽으면 안 된다 — 서비스 기준으로 이어간다.
            log.warn("알 수 없는 사용자 타임존 '{}' — 서비스 기준({})으로 폴백", timezone, serviceClock.zone());
            return serviceClock.zone();
        }
    }
}
