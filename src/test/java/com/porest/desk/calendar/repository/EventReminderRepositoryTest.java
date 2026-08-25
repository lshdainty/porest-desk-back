package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.EventReminder;
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
 * EventReminder QueryDsl 리포 슬라이스 테스트.
 * 커스텀 조건: 이벤트별 조회(minutesBefore 오름차순), 다중 이벤트 IN + 빈목록 가드,
 * 미발송 후보 리마인더(isSent=N & event.isDeleted=N & startDate <= bound — 도래 판정은
 * 스케줄러가 소유자 타임존으로), bulk delete.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        EventReminderQueryDslRepository.class})
@ActiveProfiles("test")
class EventReminderRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private EventReminderRepository repository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private UserCalendar persistCalendar(User user) {
        return em.persist(UserCalendar.createCalendar(user, "내 캘린더", null, 0, true));
    }

    private CalendarEvent persistEvent(User user, UserCalendar cal, LocalDateTime start) {
        return em.persist(CalendarEvent.createEvent(user, "일정", null, CalendarEventType.PERSONAL, null,
                start, start.plusHours(1), YNType.N, null, null, null, cal));
    }

    private EventReminder persistReminder(CalendarEvent event, String type, int minutesBefore) {
        return em.persist(EventReminder.create(event, type, minutesBefore));
    }

    @Test
    @DisplayName("findByEventId — 해당 이벤트 리마인더만 minutesBefore 오름차순으로 반환")
    void findByEventIdScopeAndOrder() {
        User user = persistUser("u1");
        UserCalendar cal = persistCalendar(user);
        CalendarEvent event = persistEvent(user, cal, NOW);
        CalendarEvent other = persistEvent(user, cal, NOW);
        // 삽입 순서와 minutesBefore 순서를 어긋나게
        persistReminder(event, "A", 30);
        persistReminder(event, "B", 10);
        persistReminder(event, "C", 60);
        persistReminder(other, "OTHER", 5); // 다른 이벤트 → 제외
        em.flush();
        em.clear();

        List<EventReminder> result = repository.findByEventId(event.getRowId());

        assertThat(result).extracting(EventReminder::getMinutesBefore)
                .containsExactly(10, 30, 60);
    }

    @Test
    @DisplayName("findByEventIds — 빈/누락 목록은 빈 리스트, IN 목록은 minutesBefore 오름차순")
    void findByEventIdsInAndEmptyGuard() {
        User user = persistUser("u1");
        UserCalendar cal = persistCalendar(user);
        CalendarEvent e1 = persistEvent(user, cal, NOW);
        CalendarEvent e2 = persistEvent(user, cal, NOW);
        CalendarEvent e3 = persistEvent(user, cal, NOW);
        persistReminder(e1, "e1-20", 20);
        persistReminder(e2, "e2-5", 5);
        persistReminder(e3, "e3-40", 40); // 조회 대상 아님
        em.flush();
        em.clear();

        assertThat(repository.findByEventIds(List.of())).isEmpty();

        List<EventReminder> result = repository.findByEventIds(List.of(e1.getRowId(), e2.getRowId()));
        assertThat(result).extracting(EventReminder::getMinutesBefore)
                .containsExactly(5, 20); // 이벤트 무관, minutesBefore 오름차순
    }

    @Test
    @DisplayName("findUnsentRemindersStartingBefore — 미발송 & 이벤트 미삭제 & startDate<=bound 만 반환(경계 포함)")
    void findUnsentRemindersStartingBefore() {
        User user = persistUser("u1");
        UserCalendar cal = persistCalendar(user);
        LocalDateTime bound = NOW.plusDays(2);

        // bound 안 → 포함 (도래 판정은 스케줄러가 소유자 타임존으로 한다)
        CalendarEvent due = persistEvent(user, cal, NOW.plusMinutes(10));
        persistReminder(due, "DUE", 15);
        CalendarEvent near = persistEvent(user, cal, NOW.plusHours(2));
        persistReminder(near, "NEAR", 15);
        // 이미 발송됨 → 제외
        EventReminder sent = persistReminder(due, "SENT", 30);
        sent.markSent();
        // bound 밖 → 제외
        CalendarEvent far = persistEvent(user, cal, NOW.plusDays(3));
        persistReminder(far, "FAR", 15);
        // 이벤트 soft-delete → 제외
        CalendarEvent deleted = persistEvent(user, cal, NOW.plusMinutes(10));
        deleted.deleteEvent();
        persistReminder(deleted, "DELETED", 15);
        // 경계: startDate == bound → loe 로 포함
        CalendarEvent boundary = persistEvent(user, cal, bound);
        persistReminder(boundary, "BOUNDARY", 15);
        em.flush();
        em.clear();

        List<EventReminder> result = repository.findUnsentRemindersStartingBefore(bound);

        assertThat(result).extracting(EventReminder::getReminderType)
                .containsExactlyInAnyOrder("DUE", "NEAR", "BOUNDARY");
    }

    @Test
    @DisplayName("deleteByEventId — 이벤트의 모든 리마인더를 삭제한다")
    void deleteByEventId() {
        User user = persistUser("u1");
        UserCalendar cal = persistCalendar(user);
        CalendarEvent event = persistEvent(user, cal, NOW);
        CalendarEvent other = persistEvent(user, cal, NOW);
        persistReminder(event, "A", 10);
        persistReminder(event, "B", 20);
        persistReminder(other, "KEEP", 10);
        em.flush();

        repository.deleteByEventId(event.getRowId());
        em.clear();

        assertThat(repository.findByEventId(event.getRowId())).isEmpty();
        assertThat(repository.findByEventId(other.getRowId())).hasSize(1);
    }

    @Test
    @DisplayName("deleteById — 단건 리마인더를 삭제한다")
    void deleteById() {
        User user = persistUser("u1");
        UserCalendar cal = persistCalendar(user);
        CalendarEvent event = persistEvent(user, cal, NOW);
        EventReminder target = persistReminder(event, "A", 10);
        EventReminder keep = persistReminder(event, "B", 20);
        em.flush();

        repository.deleteById(target.getRowId());
        em.clear();

        assertThat(repository.findById(target.getRowId())).isEmpty();
        assertThat(repository.findById(keep.getRowId())).isPresent();
    }
}
