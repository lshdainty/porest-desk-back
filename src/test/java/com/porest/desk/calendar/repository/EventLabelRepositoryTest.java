package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventLabel;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventLabel QueryDsl 리포 슬라이스 테스트.
 * 커스텀 조건: 사용자 스코프 + soft-delete 제외, 이름 중복 존재검사(exclude 옵션), sortOrder→rowId 정렬.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        EventLabelQueryDslRepository.class})
@ActiveProfiles("test")
class EventLabelRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private EventLabelRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private EventLabel persistLabel(User user, String name, int sortOrder) {
        return em.persist(EventLabel.createLabel(user, name, "#ff0000", sortOrder));
    }

    @Test
    @DisplayName("findById — soft delete 된 라벨은 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        EventLabel active = persistLabel(user, "중요", 0);
        EventLabel deleted = persistLabel(user, "임시", 1);
        deleted.deleteLabel();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("existsActiveByUserAndName — 같은 사용자·이름·미삭제일 때만 true")
    void existsActiveByUserAndName() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistLabel(user, "중요", 0);
        persistLabel(other, "남의라벨", 0);
        EventLabel deleted = persistLabel(user, "지워진라벨", 1);
        deleted.deleteLabel();
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "중요", null)).isTrue();
        // 이름 다름 → false
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "없는이름", null)).isFalse();
        // 다른 사용자의 라벨 → false (사용자 스코프)
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "남의라벨", null)).isFalse();
        // soft-delete 된 라벨 → false
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "지워진라벨", null)).isFalse();
    }

    @Test
    @DisplayName("existsActiveByUserAndName — excludeRowId 로 자기 자신을 제외하면 false(rename 시 자기 이름 허용)")
    void existsActiveByUserAndNameWithExclude() {
        User user = persistUser("u1");
        EventLabel label = persistLabel(user, "중요", 0);
        em.flush();
        em.clear();

        // 자기 자신 제외 → 중복 아님
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "중요", label.getRowId())).isFalse();
        // 다른 id 를 제외 → 여전히 중복
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "중요", label.getRowId() + 999)).isTrue();
    }

    @Test
    @DisplayName("findAllByUser — 본인 미삭제 라벨만 sortOrder→rowId 오름차순으로 반환")
    void findAllByUserScopeSoftDeleteOrder() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        EventLabel a = persistLabel(user, "A(order2)", 2);
        EventLabel b = persistLabel(user, "B(order0)", 0);
        EventLabel c = persistLabel(user, "C(order0)", 0); // sortOrder 동률 → rowId 로 tiebreak
        EventLabel deleted = persistLabel(user, "삭제", 0);
        deleted.deleteLabel();
        persistLabel(other, "남의것", 0); // 다른 사용자 → 제외
        em.flush();
        em.clear();

        List<EventLabel> result = repository.findAllByUser(user.getRowId());

        // order0(b, c: rowId 오름차순) → order2(a)
        assertThat(result).extracting(EventLabel::getLabelName)
                .containsExactly("B(order0)", "C(order0)", "A(order2)");
    }
}
