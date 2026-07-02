package com.porest.desk.notification.repository;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;
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
 * Notification QueryDsl 리포 슬라이스 테스트 — H2 에서 소유자별 조회(생성시각 내림차순·limit 100),
 * 미읽음 카운트, 참조/시각 기준 존재 판정, 일괄 읽음(bulk update), soft-delete 제외를 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        NotificationQueryDslRepository.class})
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private NotificationRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private Notification persistNoti(User user, String title, ReferenceType type, Long refId) {
        return em.persist(Notification.createNotification(user, NotificationType.SYSTEM, title, "메시지", type, refId));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        Notification n = Notification.createNotification(user, NotificationType.EVENT_REMINDER,
                "제목", "메시지", ReferenceType.CALENDAR_EVENT, 5L);
        repository.save(n);
        em.flush();
        em.clear();

        Optional<Notification> found = repository.findById(n.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        Notification n = persistNoti(user, "제목", ReferenceType.CALENDAR_EVENT, 5L);
        em.flush();

        n.deleteNotification();
        em.flush();
        em.clear();

        assertThat(repository.findById(n.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 본인의 삭제되지 않은 알림만 생성시각 내림차순으로 반환(타인 제외)")
    void findAllByUserReturnsOwnActiveOrderedByCreateAtDesc() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        // 각각 별도 flush 로 생성시각을 벌려 내림차순 정렬을 검증
        persistNoti(user, "첫번째", ReferenceType.CALENDAR_EVENT, 1L);
        em.flush();
        em.clear();
        persistNoti(user, "두번째", ReferenceType.CALENDAR_EVENT, 2L);
        em.flush();
        em.clear();
        persistNoti(user, "세번째", ReferenceType.CALENDAR_EVENT, 3L);
        em.flush();
        Notification deleted = persistNoti(user, "삭제됨", ReferenceType.CALENDAR_EVENT, 4L);
        em.flush();
        deleted.deleteNotification();
        em.flush();
        em.clear();
        persistNoti(other, "남의알림", ReferenceType.CALENDAR_EVENT, 5L);
        em.flush();
        em.clear();

        List<Notification> result = repository.findAllByUser(user.getRowId());

        assertThat(result).extracting(Notification::getTitle)
                .containsExactly("세번째", "두번째", "첫번째");
    }

    @Test
    @DisplayName("findAllByUser — 최대 100건까지만 반환한다(limit)")
    void findAllByUserLimitsTo100() {
        User user = persistUser("u1");
        for (int i = 0; i < 101; i++) {
            em.persist(Notification.createNotification(user, NotificationType.SYSTEM,
                    "n" + i, "메시지", ReferenceType.TODO, (long) i));
        }
        em.flush();
        em.clear();

        List<Notification> result = repository.findAllByUser(user.getRowId());

        assertThat(result).hasSize(100);
    }

    @Test
    @DisplayName("countUnread — 본인의 삭제되지 않은 미읽음 알림 수만 센다")
    void countUnreadCountsActiveUnreadOnly() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistNoti(user, "안읽음1", ReferenceType.TODO, 1L);
        persistNoti(user, "안읽음2", ReferenceType.TODO, 2L);
        Notification read = persistNoti(user, "읽음", ReferenceType.TODO, 3L);
        Notification deletedUnread = persistNoti(user, "삭제안읽음", ReferenceType.TODO, 4L);
        persistNoti(other, "남의안읽음", ReferenceType.TODO, 5L);
        em.flush();
        read.markRead();
        deletedUnread.deleteNotification();
        em.flush();
        em.clear();

        assertThat(repository.countUnread(user.getRowId())).isEqualTo(2L);
        assertThat(repository.countUnread(other.getRowId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("existsByUserAndReferenceAndCreatedAfter — 소유자·참조·시각(이후) 조건과 soft-delete 를 모두 반영")
    void existsByUserAndReferenceAndCreatedAfterMatchesConditions() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        Notification n = persistNoti(user, "제목", ReferenceType.CALENDAR_EVENT, 100L);
        em.flush();
        LocalDateTime createdAt = n.getCreateAt();
        em.clear();

        LocalDateTime before = createdAt.minusMinutes(1);
        LocalDateTime after = createdAt.plusMinutes(1);

        // createAt >= before → 존재
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(user.getRowId(), ReferenceType.CALENDAR_EVENT, 100L, before)).isTrue();
        // createAt < after → 없음
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(user.getRowId(), ReferenceType.CALENDAR_EVENT, 100L, after)).isFalse();
        // referenceId 다름 → 없음
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(user.getRowId(), ReferenceType.CALENDAR_EVENT, 999L, before)).isFalse();
        // referenceType 다름 → 없음
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(user.getRowId(), ReferenceType.TODO, 100L, before)).isFalse();
        // 다른 사용자 → 없음
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(other.getRowId(), ReferenceType.CALENDAR_EVENT, 100L, before)).isFalse();

        // soft-delete 후에는 존재하지 않음
        Notification managed = em.find(Notification.class, n.getRowId());
        managed.deleteNotification();
        em.flush();
        em.clear();
        assertThat(repository.existsByUserAndReferenceAndCreatedAfter(user.getRowId(), ReferenceType.CALENDAR_EVENT, 100L, before)).isFalse();
    }

    @Test
    @DisplayName("markAllRead — 본인의 삭제되지 않은 미읽음만 읽음 처리(타인·기읽음·삭제 제외)")
    void markAllReadMarksOnlyActiveUnreadOfUser() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        Notification unread1 = persistNoti(user, "안읽음1", ReferenceType.TODO, 1L);
        persistNoti(user, "안읽음2", ReferenceType.TODO, 2L);
        Notification alreadyRead = persistNoti(user, "이미읽음", ReferenceType.TODO, 3L);
        Notification deletedUnread = persistNoti(user, "삭제안읽음", ReferenceType.TODO, 4L);
        persistNoti(other, "남의안읽음", ReferenceType.TODO, 5L);
        em.flush();
        alreadyRead.markRead();
        deletedUnread.deleteNotification();
        em.flush();
        em.clear();

        repository.markAllRead(user.getRowId());
        em.flush();
        em.clear(); // bulk update 는 영속성 컨텍스트를 우회하므로 반드시 clear 후 재조회

        // 본인 미읽음 0
        assertThat(repository.countUnread(user.getRowId())).isEqualTo(0L);
        // 타인은 영향 없음
        assertThat(repository.countUnread(other.getRowId())).isEqualTo(1L);
        // 읽음 처리된 항목은 readAt 이 채워진다
        Notification reloadedUnread1 = em.find(Notification.class, unread1.getRowId());
        assertThat(reloadedUnread1.getIsRead()).isEqualTo(YNType.Y);
        assertThat(reloadedUnread1.getReadAt()).isNotNull();
        // 삭제된 미읽음은 대상에서 제외 → 여전히 미읽음(N)
        Notification reloadedDeleted = em.find(Notification.class, deletedUnread.getRowId());
        assertThat(reloadedDeleted.getIsRead()).isEqualTo(YNType.N);
    }
}
