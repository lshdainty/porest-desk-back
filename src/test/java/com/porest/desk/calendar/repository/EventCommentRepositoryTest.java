package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.EventComment;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventComment QueryDsl 리포 슬라이스 테스트.
 * 커스텀 조건: 이벤트 스코프 + soft-delete(is_deleted=N) 제외 + createAt 오름차순 정렬.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        EventCommentQueryDslRepository.class})
@ActiveProfiles("test")
class EventCommentRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private EventCommentRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private CalendarEvent persistEvent(User user, UserCalendar cal, String title) {
        return em.persist(CalendarEvent.createEvent(user, title, null, CalendarEventType.PERSONAL, null,
                LocalDateTime.of(2026, 6, 15, 10, 0), LocalDateTime.of(2026, 6, 15, 11, 0),
                YNType.N, null, null, null, cal));
    }

    private EventComment persistComment(CalendarEvent event, User user, String content) {
        return em.persist(EventComment.create(event, user, null, content));
    }

    /** createAt 은 @CreatedDate(updatable=false) 라 persist 시점에 auditing 이 덮어쓴다.
     *  정렬 검증을 위해 flush 후 native update 로 명시적 create_at 을 부여한다. */
    private void overrideCreateAt(Long rowId, LocalDateTime createAt) {
        em.getEntityManager()
                .createNativeQuery("update event_comment set create_at = ?1 where row_id = ?2")
                .setParameter(1, createAt)
                .setParameter(2, rowId)
                .executeUpdate();
    }

    @Test
    @DisplayName("findById — soft delete 된 댓글은 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        UserCalendar cal = em.persist(UserCalendar.createCalendar(user, "내 캘린더", null, 0, true));
        CalendarEvent event = persistEvent(user, cal, "회의");
        EventComment active = persistComment(event, user, "살아있는 댓글");
        EventComment deleted = persistComment(event, user, "삭제된 댓글");
        deleted.deleteComment();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByEvent — 해당 이벤트의 미삭제 댓글만 createAt 오름차순으로 반환")
    void findAllByEventScopeSoftDeleteOrder() {
        User user = persistUser("u1");
        UserCalendar cal = em.persist(UserCalendar.createCalendar(user, "내 캘린더", null, 0, true));
        CalendarEvent event = persistEvent(user, cal, "회의");
        CalendarEvent other = persistEvent(user, cal, "다른 회의");

        // 삽입 순서와 createAt 순서를 어긋나게 해 ORDER BY createAt 을 검증
        EventComment third = persistComment(event, user, "셋");
        EventComment first = persistComment(event, user, "하나");
        EventComment second = persistComment(event, user, "둘");
        EventComment removed = persistComment(event, user, "삭제됨");
        removed.deleteComment();
        persistComment(other, user, "다른 이벤트 댓글"); // 스코프 밖 → 제외
        em.flush();

        overrideCreateAt(first.getRowId(), LocalDateTime.of(2026, 6, 15, 10, 0));
        overrideCreateAt(second.getRowId(), LocalDateTime.of(2026, 6, 15, 11, 0));
        overrideCreateAt(third.getRowId(), LocalDateTime.of(2026, 6, 15, 12, 0));
        em.clear();

        List<EventComment> result = repository.findAllByEvent(event.getRowId());

        assertThat(result).extracting(EventComment::getContent)
                .containsExactly("하나", "둘", "셋");
    }
}
