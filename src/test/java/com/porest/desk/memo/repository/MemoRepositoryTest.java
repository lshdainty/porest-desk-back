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
        return em.persist(Memo.createMemo(user, title, content, null, null, null));
    }

    private MemoTag persistTag(User user, String name) {
        return em.persist(MemoTag.createTag(user, name, "#ffffff"));
    }

    private Memo persistTaggedMemo(User user, String title, String tagName, MemoTag tag) {
        return em.persist(Memo.createMemo(user, title, "c", tagName, tag, null));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        Memo memo = Memo.createMemo(user, "제목", "내용", null, null, null);
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
        managedA.updateMemo("A수정", "c2", null, null, null);
        em.flush();
        em.clear();

        List<Memo> result = repository.findAllByUser(user.getRowId(), null);

        assertThat(result).extracting(Memo::getTitle).containsExactly("A수정", "B");
    }

    // ── 태그 개명·삭제 동기화 (QA #98) ────────────────────────────────────────

    /**
     * 개명은 <b>FK 로 이어진 행과 이름만 같은 행을 함께</b> 옮긴다.
     *
     * <p>둘로 나눠 보는 이유 — 백필 전에는 FK 가 비어 있고 이름만 있는 옛 메모가 있다.
     * 하나만 보면 그 행이 개명에서 빠지고, 그 메모를 다음에 저장할 때 서버가 옛 이름의 태그를
     * 다시 만든다(QA #88 과 같은 되살아남).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code renameTag} 의 WHERE 에서
     * {@code .or(memo.tag.eq(fromTagName))} 를 지우면 `이름만` 행이 안 옮겨져 깨지고,
     * {@code memo.memoTag.rowId.eq(tagRowId)} 쪽만 남기면 반대로 `FK만` 행이 깨진다.
     */
    @Test
    @DisplayName("renameTag — FK 로 이어진 메모와 이름만 같은 메모를 함께 옮긴다")
    void renameTagMovesBothFkAndNameOnlyRows() {
        User user = persistUser("u1");
        MemoTag tag = persistTag(user, "업무");
        Memo linked = persistTaggedMemo(user, "FK로 이어진 메모", "업무", tag);
        Memo nameOnly = persistTaggedMemo(user, "이름만 있는 옛 메모", "업무", null);
        Memo untouched = persistTaggedMemo(user, "다른 태그", "개인", null);
        em.flush();
        em.clear();

        long moved = repository.renameTag(user.getRowId(), tag.getRowId(), "업무", "회사");

        em.clear();
        assertThat(moved).isEqualTo(2);
        assertThat(em.find(Memo.class, linked.getRowId()).getTag()).isEqualTo("회사");
        assertThat(em.find(Memo.class, nameOnly.getRowId()).getTag()).isEqualTo("회사");
        assertThat(em.find(Memo.class, untouched.getRowId()).getTag()).isEqualTo("개인");
    }

    @Test
    @DisplayName("renameTag — 남의 메모와 삭제된 메모는 건드리지 않는다")
    void renameTagRespectsOwnerAndSoftDelete() {
        User mine = persistUser("u1");
        User other = persistUser("u2");
        MemoTag tag = persistTag(mine, "업무");
        Memo theirs = persistTaggedMemo(other, "남의 메모", "업무", null);
        Memo deleted = persistTaggedMemo(mine, "지운 메모", "업무", tag);
        deleted.deleteMemo();
        em.flush();
        em.clear();

        long moved = repository.renameTag(mine.getRowId(), tag.getRowId(), "업무", "회사");

        em.clear();
        assertThat(moved).isZero();
        assertThat(em.find(Memo.class, theirs.getRowId()).getTag()).isEqualTo("업무");
        assertThat(em.find(Memo.class, deleted.getRowId()).getTag()).isEqualTo("업무");
    }

    /**
     * QA #88 — 태그를 지울 때 <b>문자열과 FK 를 함께</b> 끊는다. 한쪽만 끊으면 나머지 한쪽이
     * 다음 저장에서 그 태그를 되살린다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code clearTag} 에서 {@code .setNull(memo.tag)} 를
     * 지우면 이름이 남아 깨지고, {@code .setNull(memo.memoTag)} 를 지우면 FK 가 남아 깨진다.
     */
    @Test
    @DisplayName("clearTag — tag 문자열과 FK 를 함께 비운다")
    void clearTagClearsBothStringAndFk() {
        User user = persistUser("u1");
        MemoTag tag = persistTag(user, "업무");
        Memo linked = persistTaggedMemo(user, "FK로 이어진 메모", "업무", tag);
        Memo nameOnly = persistTaggedMemo(user, "이름만 있는 옛 메모", "업무", null);
        em.flush();
        em.clear();

        long cleared = repository.clearTag(user.getRowId(), tag.getRowId(), "업무");

        em.clear();
        assertThat(cleared).isEqualTo(2);
        Memo reloaded = em.find(Memo.class, linked.getRowId());
        assertThat(reloaded.getTag()).isNull();
        assertThat(reloaded.getMemoTag()).isNull();
        assertThat(em.find(Memo.class, nameOnly.getRowId()).getTag()).isNull();
    }

    @Test
    @DisplayName("clearTag — 남의 메모는 건드리지 않는다")
    void clearTagRespectsOwner() {
        User mine = persistUser("u1");
        User other = persistUser("u2");
        MemoTag tag = persistTag(mine, "업무");
        Memo theirs = persistTaggedMemo(other, "남의 메모", "업무", null);
        em.flush();
        em.clear();

        repository.clearTag(mine.getRowId(), tag.getRowId(), "업무");

        em.clear();
        assertThat(em.find(Memo.class, theirs.getRowId()).getTag()).isEqualTo("업무");
    }

    @Test
    @DisplayName("findById·findAllByUser — 태그 마스터를 함께 끌고 온다(fetch join)")
    void readsFetchTheTagMaster() {
        User user = persistUser("u1");
        MemoTag tag = persistTag(user, "업무");
        Memo memo = persistTaggedMemo(user, "태그 있는 메모", "업무", tag);
        em.flush();
        em.clear();

        assertThat(repository.findById(memo.getRowId()))
                .get()
                .extracting(m -> m.getMemoTag().getTagName()).isEqualTo("업무");
        assertThat(repository.findAllByUser(user.getRowId(), null))
                .singleElement()
                .extracting(m -> m.getMemoTag().getRowId()).isEqualTo(tag.getRowId());
    }
}
