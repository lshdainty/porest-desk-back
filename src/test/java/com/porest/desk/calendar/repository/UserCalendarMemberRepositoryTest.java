package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.type.CalendarRole;
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

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserCalendarMember QueryDsl 리포 슬라이스 테스트.
 * 커스텀 조건: 캘린더+사용자 단건 조회, 캘린더 스코프 멤버목록(joinedAt 오름차순),
 * 사용자가 속한 캘린더 id 목록 — 모두 soft-delete(is_deleted=N) 제외.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        UserCalendarMemberQueryDslRepository.class})
@ActiveProfiles("test")
class UserCalendarMemberRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserCalendarMemberRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private UserCalendar persistCalendar(User owner, String name) {
        return em.persist(UserCalendar.createCalendar(owner, name, null, 0, true));
    }

    private UserCalendarMember persistMember(UserCalendar cal, User user, CalendarRole role) {
        return em.persist(UserCalendarMember.create(cal, user, role));
    }

    /** joinedAt 은 factory 에서 now() 로 세팅되므로 정렬 검증을 위해 persist 전에 명시적으로 덮어쓴다. */
    private UserCalendarMember persistMemberJoinedAt(UserCalendar cal, User user, CalendarRole role, LocalDateTime joinedAt) {
        UserCalendarMember member = UserCalendarMember.create(cal, user, role);
        setJoinedAt(member, joinedAt);
        return em.persist(member);
    }

    private static void setJoinedAt(UserCalendarMember member, LocalDateTime joinedAt) {
        try {
            Field field = UserCalendarMember.class.getDeclaredField("joinedAt");
            field.setAccessible(true);
            field.set(member, joinedAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("findByCalendarAndUser — 캘린더+사용자 조합으로 조회, 불일치/soft-delete 는 empty")
    void findByCalendarAndUser() {
        User owner = persistUser("owner");
        User member = persistUser("member");
        User stranger = persistUser("stranger");
        UserCalendar cal = persistCalendar(owner, "공유 캘린더");
        UserCalendar otherCal = persistCalendar(owner, "다른 캘린더");
        persistMember(cal, member, CalendarRole.EDIT);
        UserCalendarMember removed = persistMember(cal, stranger, CalendarRole.READ);
        removed.removeMember();
        em.flush();
        em.clear();

        assertThat(repository.findByCalendarAndUser(cal.getRowId(), member.getRowId())).isPresent();
        // 다른 캘린더 → empty
        assertThat(repository.findByCalendarAndUser(otherCal.getRowId(), member.getRowId())).isEmpty();
        // 멤버 아님 → empty
        assertThat(repository.findByCalendarAndUser(cal.getRowId(), owner.getRowId())).isEmpty();
        // soft-delete 된 멤버 → empty
        assertThat(repository.findByCalendarAndUser(cal.getRowId(), stranger.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByCalendar — 해당 캘린더의 미삭제 멤버만 joinedAt 오름차순으로 반환")
    void findAllByCalendarScopeSoftDeleteOrder() {
        User owner = persistUser("owner");
        User u1 = persistUser("u1");
        User u2 = persistUser("u2");
        User u3 = persistUser("u3");
        User u4 = persistUser("u4");
        UserCalendar cal = persistCalendar(owner, "공유 캘린더");
        UserCalendar otherCal = persistCalendar(owner, "다른 캘린더");

        // 삽입 순서와 joinedAt 순서를 어긋나게 해 ORDER BY joinedAt 을 검증
        persistMemberJoinedAt(cal, u2, CalendarRole.EDIT, LocalDateTime.of(2026, 6, 15, 12, 0));
        persistMemberJoinedAt(cal, u1, CalendarRole.OWNER, LocalDateTime.of(2026, 6, 15, 10, 0));
        persistMemberJoinedAt(cal, u3, CalendarRole.READ, LocalDateTime.of(2026, 6, 15, 14, 0));
        UserCalendarMember removed = persistMemberJoinedAt(cal, u4, CalendarRole.READ, LocalDateTime.of(2026, 6, 15, 11, 0));
        removed.removeMember(); // soft-delete → 제외
        persistMemberJoinedAt(otherCal, u1, CalendarRole.OWNER, LocalDateTime.of(2026, 6, 15, 9, 0)); // 다른 캘린더 → 제외
        em.flush();
        em.clear();

        List<UserCalendarMember> result = repository.findAllByCalendar(cal.getRowId());

        assertThat(result).extracting(m -> m.getUser().getUserId())
                .containsExactly("u1", "u2", "u3");
    }

    @Test
    @DisplayName("findCalendarIdsByUser — 사용자가 속한 미삭제 캘린더 id 만 반환")
    void findCalendarIdsByUser() {
        User user = persistUser("user");
        User other = persistUser("other");
        UserCalendar cal1 = persistCalendar(user, "cal1");
        UserCalendar cal2 = persistCalendar(user, "cal2");
        UserCalendar cal3 = persistCalendar(user, "cal3");
        UserCalendar cal4 = persistCalendar(other, "cal4");
        persistMember(cal1, user, CalendarRole.OWNER);
        persistMember(cal2, user, CalendarRole.EDIT);
        UserCalendarMember removed = persistMember(cal3, user, CalendarRole.READ);
        removed.removeMember(); // soft-delete → 제외
        persistMember(cal4, other, CalendarRole.OWNER); // 다른 사용자 → 제외
        em.flush();
        em.clear();

        List<Long> result = repository.findCalendarIdsByUser(user.getRowId());

        assertThat(result).containsExactlyInAnyOrder(cal1.getRowId(), cal2.getRowId());
    }
}
