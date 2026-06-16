package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CalendarEvent 리포 슬라이스 테스트 — 기간 "겹침(overlap)" 조회 조건 검증.
 * 조건: event.start &lt;= queryEnd AND event.end &gt;= queryStart (단순 포함이 아니라 겹침).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CalendarEventQueryDslRepository.class})
@ActiveProfiles("test")
class CalendarEventRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CalendarEventRepository repository;

    private LocalDateTime d(int day, int hour) {
        return LocalDateTime.of(2026, 6, day, hour, 0);
    }

    private void persistEvent(User user, UserCalendar cal, String title, LocalDateTime start, LocalDateTime end) {
        em.persist(CalendarEvent.createEvent(user, title, null, CalendarEventType.PERSONAL, null,
                start, end, YNType.N, null, null, null, cal));
    }

    @Test
    @DisplayName("findByUserAndDateRange — 조회 기간과 겹치는 일정만 시작일 오름차순으로 반환")
    void overlapQuery() {
        User user = em.persist(User.createUser(null, "u1", "테스터", "u1@porest.com"));
        UserCalendar cal = em.persist(UserCalendar.createCalendar(user, "내 캘린더", null, 0, true));

        persistEvent(user, cal, "안(12~13)", d(12, 10), d(13, 10));   // 완전 포함 → 포함
        persistEvent(user, cal, "앞겹침(05~11)", d(5, 10), d(11, 10)); // 시작 전~끝 안 → 겹침 포함
        persistEvent(user, cal, "뒤겹침(19~25)", d(19, 10), d(25, 10));// 시작 안~끝 후 → 겹침 포함
        persistEvent(user, cal, "이전(01~05)", d(1, 10), d(5, 9));      // 완전 이전 → 제외
        persistEvent(user, cal, "이후(25~30)", d(25, 10), d(30, 10));   // 완전 이후 → 제외
        em.flush();
        em.clear();

        List<CalendarEvent> result = repository.findByUserAndDateRange(
                user.getRowId(), d(10, 0), d(20, 23));

        assertThat(result).extracting(CalendarEvent::getTitle)
                .containsExactly("앞겹침(05~11)", "안(12~13)", "뒤겹침(19~25)");
    }
}
