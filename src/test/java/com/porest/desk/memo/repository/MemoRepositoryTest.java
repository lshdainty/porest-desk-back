package com.porest.desk.memo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.memo.domain.Memo;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memo QueryDsl 리포 슬라이스 테스트 — H2 에서 소유자/검색 필터, soft-delete 제외,
 * 고정(pin) 우선 + 수정시각 내림차순 정렬을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        MemoQueryDslRepository.class})
@ActiveProfiles("test")
class MemoRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private MemoRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private Memo persistMemo(User user, String title, String content) {
        return em.persist(Memo.createMemo(user, title, content, null, null));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        Memo memo = Memo.createMemo(user, "제목", "내용", null, null);
        repository.save(memo);
        em.flush();
        em.clear();

        Optional<Memo> found = repository.findById(memo.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        Memo memo = persistMemo(user, "제목", "내용");
        em.flush();

        repository.delete(memo); // deleteMemo() 로 is_deleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findById(memo.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 본인의 삭제되지 않은 메모만 반환한다(타인·soft-delete 제외)")
    void findAllByUserReturnsOnlyOwnAndActive() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistMemo(user, "내꺼", "c");
        Memo deleted = persistMemo(user, "삭제됨", "c");
        persistMemo(other, "남의꺼", "c");
        em.flush();
        deleted.deleteMemo();
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null);

        assertThat(result).extracting(Memo::getTitle).containsExactly("내꺼");
    }

    @Test
    @DisplayName("findAllByUser — 검색어가 제목 또는 내용에 부분일치하는 메모만 반환")
    void findAllByUserSearchMatchesTitleOrContent() {
        User user = persistUser("u1");
        persistMemo(user, "아침 커피", "내용");      // 제목 일치
        persistMemo(user, "회의록", "커피 사올 것");  // 내용 일치
        persistMemo(user, "점심", "김밥");           // 불일치
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), "커피");

        assertThat(result).extracting(Memo::getTitle)
                .containsExactlyInAnyOrder("아침 커피", "회의록");
    }

    @Test
    @DisplayName("findAllByUser — 고정(pin) 메모가 수정시각과 무관하게 항상 먼저 온다")
    void findAllByUserPinnedFirst() {
        User user = persistUser("u1");
        // 고정 메모를 먼저 만들고 pin(수정시각 t1) → 이후 일반 메모(수정시각 t2 > t1)
        Memo pinned = persistMemo(user, "고정", "c");
        em.flush();
        pinned.togglePin();
        em.flush();
        em.clear();
        persistMemo(user, "최신", "c");
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null);

        // 순수 수정시각 정렬이면 [최신, 고정] 이지만, is_pinned 가 1차 정렬이라 [고정, 최신]
        assertThat(result).extracting(Memo::getTitle).containsExactly("고정", "최신");
    }

    @Test
    @DisplayName("findAllByUser — 같은 pin 그룹에서는 수정시각 내림차순(수정 반영)으로 정렬")
    void findAllByUserOrdersByModifyAtDescWithinSamePin() {
        User user = persistUser("u1");
        Memo a = persistMemo(user, "A", "c");
        em.flush();
        em.clear();
        persistMemo(user, "B", "c"); // A 이후 생성 → B 수정시각이 더 최신
        em.flush();
        em.clear();

        // A 를 수정해 수정시각을 최신으로 끌어올린다
        Memo managedA = em.find(Memo.class, a.getRowId());
        managedA.updateMemo("A수정", "c2", null, null);
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null);

        assertThat(result).extracting(Memo::getTitle).containsExactly("A수정", "B");
    }
}
