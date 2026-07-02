package com.porest.desk.memo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
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
 * MemoFolder QueryDsl 리포 슬라이스 테스트 — H2 에서 소유자 정렬(sortOrder→rowId),
 * soft-delete 제외, 그리고 부모/이름 기준 중복 존재(existsActive...) 판정을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        MemoFolderQueryDslRepository.class})
@ActiveProfiles("test")
class MemoFolderRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private MemoFolderRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private MemoFolder persistFolder(User user, MemoFolder parent, String name, int sortOrder) {
        MemoFolder folder = MemoFolder.createFolder(user, parent, name);
        folder.updateFolder(parent, name, sortOrder); // createFolder 는 sortOrder=0 이므로 원하는 값으로 세팅
        return em.persist(folder);
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        MemoFolder folder = MemoFolder.createFolder(user, null, "업무");
        repository.save(folder);
        em.flush();
        em.clear();

        Optional<MemoFolder> found = repository.findById(folder.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getFolderName()).isEqualTo("업무");
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        MemoFolder folder = em.persist(MemoFolder.createFolder(user, null, "업무"));
        em.flush();

        repository.delete(folder); // deleteFolder() 로 is_deleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findById(folder.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 본인 폴더만 sortOrder 오름차순, 동률이면 rowId 오름차순으로 반환")
    void findAllByUserOrderedBySortOrderThenRowId() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistFolder(user, null, "C정렬2", 2); // 먼저 저장(rowId 작음), sortOrder 큼
        persistFolder(user, null, "A정렬0", 0);
        persistFolder(user, null, "B정렬0", 0);
        MemoFolder deleted = persistFolder(user, null, "삭제", 0);
        persistFolder(other, null, "남의폴더", 0);
        em.flush();
        deleted.deleteFolder();
        em.flush();
        em.clear();

        List<MemoFolder> result = repository.findAllByUser(user.getRowId());

        // sortOrder ASC → 0,0,2 / 동률(0) 은 rowId ASC → A정렬0, B정렬0
        assertThat(result).extracting(MemoFolder::getFolderName)
                .containsExactly("A정렬0", "B정렬0", "C정렬2");
    }

    @Test
    @DisplayName("existsActive — 루트(parent=null)에서 동일 이름 존재 판정(이름·소유자 구분)")
    void existsActiveRootLevel() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistFolder(user, null, "업무", 0);
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "업무", null)).isTrue();
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "개인", null)).isFalse();
        assertThat(repository.existsActiveByUserAndParentAndName(other.getRowId(), null, "업무", null)).isFalse();
    }

    @Test
    @DisplayName("existsActive — parent 지정 시 해당 부모 하위에서만 판정(다른 부모·루트와 구분)")
    void existsActiveUnderParent() {
        User user = persistUser("u1");
        MemoFolder parent = persistFolder(user, null, "부모", 0);
        MemoFolder otherParent = persistFolder(user, null, "다른부모", 0);
        em.flush();
        persistFolder(user, parent, "자식", 0);
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), parent.getRowId(), "자식", null)).isTrue();
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), otherParent.getRowId(), "자식", null)).isFalse();
        // 하위 폴더 이름을 루트 기준으로 조회하면 없음
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "자식", null)).isFalse();
    }

    @Test
    @DisplayName("existsActive — soft-delete 된 폴더는 판정에서 제외된다")
    void existsActiveExcludesDeleted() {
        User user = persistUser("u1");
        MemoFolder folder = persistFolder(user, null, "업무", 0);
        em.flush();
        folder.deleteFolder();
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "업무", null)).isFalse();
    }

    @Test
    @DisplayName("existsActive — excludeRowId 로 자기 자신은 제외(이름 변경 검증 시나리오)")
    void existsActiveWithExcludeRowId() {
        User user = persistUser("u1");
        MemoFolder folder = persistFolder(user, null, "업무", 0);
        em.flush();
        em.clear();

        // 자기 자신을 제외하면 충돌 없음 → false
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "업무", folder.getRowId())).isFalse();
        // 제외 없으면 존재 → true
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "업무", null)).isTrue();
        // 다른 id 를 제외해도 충돌 폴더는 남아있음 → true
        assertThat(repository.existsActiveByUserAndParentAndName(user.getRowId(), null, "업무", folder.getRowId() + 999)).isTrue();
    }
}
