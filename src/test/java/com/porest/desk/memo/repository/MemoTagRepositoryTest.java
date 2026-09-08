package com.porest.desk.memo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoTag;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoTag QueryDsl 리포 슬라이스 테스트 — 소유권 · soft-delete 제외 · 이름 정렬,
 * 이름 중복 존재확인(자기 제외), 그리고 <b>FK 기준</b> 사용 수 집계.
 *
 * <p>{@code TodoTagRepositoryTest} 의 미러지만 집계가 다르다 — 매핑 테이블이 없어
 * {@code memo} 한 테이블만 센다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        MemoTagQueryDslRepository.class})
@ActiveProfiles("test")
class MemoTagRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private MemoTagRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private MemoTag persistTag(User user, String name) {
        return em.persist(MemoTag.createTag(user, name, "#ffffff"));
    }

    private Memo persistMemo(User user, String title, MemoTag tag) {
        return em.persist(Memo.createMemo(user, title, "c",
                tag == null ? null : tag.getTagName(), tag, null));
    }

    @Test
    @DisplayName("findById — soft-delete 된 태그는 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        MemoTag active = persistTag(user, "active");
        MemoTag deleted = persistTag(user, "deleted");
        deleted.deleteTag();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 내 활성 태그만 이름 오름차순으로 온다")
    void findAllByUserFiltersAndSorts() {
        User mine = persistUser("u1");
        User other = persistUser("u2");
        persistTag(mine, "나중");
        persistTag(mine, "가장먼저");
        MemoTag deleted = persistTag(mine, "지운것");
        deleted.deleteTag();
        persistTag(other, "남의것");
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(mine.getRowId()))
                .extracting(MemoTag::getTagName)
                .containsExactly("가장먼저", "나중");
    }

    @Test
    @DisplayName("existsActiveByUserAndName — 삭제된 같은 이름은 재사용을 막지 않는다")
    void existsIgnoresSoftDeleted() {
        User user = persistUser("u1");
        MemoTag deleted = persistTag(user, "업무");
        deleted.deleteTag();
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "업무", null)).isFalse();
    }

    @Test
    @DisplayName("existsActiveByUserAndName — 자기 자신은 제외하고 본다(개명에서 쓰는 자리)")
    void existsExcludesSelf() {
        User user = persistUser("u1");
        MemoTag tag = persistTag(user, "업무");
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "업무", tag.getRowId())).isFalse();
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "업무", null)).isTrue();
    }

    @Test
    @DisplayName("findActiveByUserAndName — 남의 같은 이름 태그는 안 걸린다")
    void findActiveIsScopedToOwner() {
        User mine = persistUser("u1");
        User other = persistUser("u2");
        MemoTag ours = persistTag(mine, "업무");
        persistTag(other, "업무");
        em.flush();
        em.clear();

        assertThat(repository.findActiveByUserAndName(mine.getRowId(), "업무"))
                .get().extracting(MemoTag::getRowId).isEqualTo(ours.getRowId());
    }

    /**
     * 사용 수는 <b>FK</b> 로 센다 — 이름으로 세면 개명하는 순간 0 이 된다(할 일이 겪은 QA #79).
     * 소유권 축은 <b>메모 주인</b>이다: 태그 주인으로 걸면 남의 메모가 섞인다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code countMemosByTag} 의
     * {@code memo.isDeleted.eq(YNType.N)} 을 지우면 지운 메모까지 세어 2 가 나오고,
     * {@code memo.user.rowId.eq(userRowId)} 를 지우면 남의 메모까지 세어 깨진다.
     */
    @Test
    @DisplayName("countMemosByTag — FK 로 세고, 지운 메모와 남의 메모는 빼고 센다")
    void countMemosByTagCountsLiveOwnMemosOnly() {
        User mine = persistUser("u1");
        User other = persistUser("u2");
        MemoTag tag = persistTag(mine, "업무");
        MemoTag unused = persistTag(mine, "안쓰는것");
        persistMemo(mine, "살아있는 메모", tag);
        Memo deleted = persistMemo(mine, "지운 메모", tag);
        deleted.deleteMemo();
        persistMemo(other, "남의 메모가 내 태그를 가리킴", tag);
        em.flush();
        em.clear();

        Map<Long, Long> usage = repository.countMemosByTag(mine.getRowId());

        assertThat(usage).containsEntry(tag.getRowId(), 1L);
        assertThat(usage).doesNotContainKey(unused.getRowId()); // 서비스가 0 으로 채운다
    }

    @Test
    @DisplayName("countMemosByTag — 태그 없는 메모는 집계에 끼지 않는다")
    void countMemosByTagIgnoresUntagged() {
        User user = persistUser("u1");
        persistMemo(user, "태그 없는 메모", null);
        em.flush();
        em.clear();

        assertThat(repository.countMemosByTag(user.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("save 후 findAllByUser 로 조회된다")
    void saveAndFind() {
        User user = persistUser("u1");
        repository.save(MemoTag.createTag(user, "새태그", null));
        em.flush();
        em.clear();

        List<MemoTag> tags = repository.findAllByUser(user.getRowId());
        assertThat(tags).singleElement().extracting(MemoTag::getTagName).isEqualTo("새태그");
        assertThat(tags.get(0).getColor()).isNull();
    }
}
