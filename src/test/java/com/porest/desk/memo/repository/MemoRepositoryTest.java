package com.porest.desk.memo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoFolder;
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
 * Memo QueryDsl 리포 슬라이스 테스트 — H2 에서 소유자/폴더/검색 필터, soft-delete 제외,
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

    private Memo persistMemo(User user, MemoFolder folder, String title, String content) {
        return em.persist(Memo.createMemo(user, folder, title, content, null, null));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        Memo memo = Memo.createMemo(user, null, "제목", "내용", null, null);
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
        Memo memo = persistMemo(user, null, "제목", "내용");
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
        persistMemo(user, null, "내꺼", "c");
        Memo deleted = persistMemo(user, null, "삭제됨", "c");
        persistMemo(other, null, "남의꺼", "c");
        em.flush();
        deleted.deleteMemo();
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null, null);

        assertThat(result).extracting(Memo::getTitle).containsExactly("내꺼");
    }

    @Test
    @DisplayName("findAllByUser — folderId 지정 시 해당 폴더 메모만, null 이면 전체 반환")
    void findAllByUserFiltersByFolder() {
        User user = persistUser("u1");
        MemoFolder folder = em.persist(MemoFolder.createFolder(user, null, "업무"));
        em.flush();
        persistMemo(user, folder, "폴더메모", "c");
        persistMemo(user, null, "루트메모", "c");
        em.flush();
        em.clear();

        List<Memo> inFolder = repository.findAllByUser(user.getRowId(), folder.getRowId(), null);
        assertThat(inFolder).extracting(Memo::getTitle).containsExactly("폴더메모");

        List<Memo> all = repository.findAllByUser(user.getRowId(), null, null);
        assertThat(all).extracting(Memo::getTitle).containsExactlyInAnyOrder("폴더메모", "루트메모");
    }

    @Test
    @DisplayName("findAllByUser — 검색어가 제목 또는 내용에 부분일치하는 메모만 반환")
    void findAllByUserSearchMatchesTitleOrContent() {
        User user = persistUser("u1");
        persistMemo(user, null, "아침 커피", "내용");      // 제목 일치
        persistMemo(user, null, "회의록", "커피 사올 것");  // 내용 일치
        persistMemo(user, null, "점심", "김밥");           // 불일치
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null, "커피");

        assertThat(result).extracting(Memo::getTitle)
                .containsExactlyInAnyOrder("아침 커피", "회의록");
    }

    @Test
    @DisplayName("findAllByUser — 고정(pin) 메모가 수정시각과 무관하게 항상 먼저 온다")
    void findAllByUserPinnedFirst() {
        User user = persistUser("u1");
        // 고정 메모를 먼저 만들고 pin(수정시각 t1) → 이후 일반 메모(수정시각 t2 > t1)
        Memo pinned = persistMemo(user, null, "고정", "c");
        em.flush();
        pinned.togglePin();
        em.flush();
        em.clear();
        persistMemo(user, null, "최신", "c");
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null, null);

        // 순수 수정시각 정렬이면 [최신, 고정] 이지만, is_pinned 가 1차 정렬이라 [고정, 최신]
        assertThat(result).extracting(Memo::getTitle).containsExactly("고정", "최신");
    }

    @Test
    @DisplayName("findAllByUser — 같은 pin 그룹에서는 수정시각 내림차순(수정 반영)으로 정렬")
    void findAllByUserOrdersByModifyAtDescWithinSamePin() {
        User user = persistUser("u1");
        Memo a = persistMemo(user, null, "A", "c");
        em.flush();
        em.clear();
        persistMemo(user, null, "B", "c"); // A 이후 생성 → B 수정시각이 더 최신
        em.flush();
        em.clear();

        // A 를 수정해 수정시각을 최신으로 끌어올린다
        Memo managedA = em.find(Memo.class, a.getRowId());
        managedA.updateMemo(null, "A수정", "c2", null, null);
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null, null);

        assertThat(result).extracting(Memo::getTitle).containsExactly("A수정", "B");
    }
}
