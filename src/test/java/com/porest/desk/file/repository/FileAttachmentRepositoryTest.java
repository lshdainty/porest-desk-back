package com.porest.desk.file.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.type.ReferenceType;
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
 * FileAttachment QueryDsl 리포 슬라이스 테스트 — H2 에서 참조(referenceType/referenceRowId)별 조회,
 * 소유자별 조회, soft-delete 제외 및 rowId 기준 정렬(참조:ASC, 소유자:DESC)을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        FileAttachmentQueryDslRepository.class})
@ActiveProfiles("test")
class FileAttachmentRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private FileAttachmentRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private FileAttachment persistFile(User user, ReferenceType type, Long refId, String originalName) {
        return em.persist(FileAttachment.create(user, originalName, "stored_" + originalName,
                "/path/" + originalName, "image/png", 1024L, type, refId));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        FileAttachment file = FileAttachment.create(user, "photo.png", "s.png", "/p/s.png",
                "image/png", 2048L, ReferenceType.MEMO_ATTACHMENT, 10L);
        repository.save(file);
        em.flush();
        em.clear();

        Optional<FileAttachment> found = repository.findById(file.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalName()).isEqualTo("photo.png");
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        FileAttachment file = persistFile(user, ReferenceType.MEMO_ATTACHMENT, 10L, "photo.png");
        em.flush();

        file.deleteFile(); // 리포에 delete 없음 → 도메인 메서드로 is_deleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findById(file.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findByReference — 동일 참조(type+refId)의 삭제되지 않은 파일만 rowId 오름차순으로 반환")
    void findByReferenceReturnsMatchingActiveOrderedByRowIdAsc() {
        User user = persistUser("u1");
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 100L, "m1.png");       // 매칭
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 100L, "m2.png");       // 매칭(rowId 더 큼)
        persistFile(user, ReferenceType.EXPENSE_RECEIPT, 100L, "receipt.png");  // type 다름
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 200L, "other.png");    // refId 다름
        FileAttachment deleted = persistFile(user, ReferenceType.MEMO_ATTACHMENT, 100L, "del.png");
        em.flush();
        deleted.deleteFile();
        em.flush();
        em.clear();

        List<FileAttachment> result = repository.findByReference(ReferenceType.MEMO_ATTACHMENT, 100L);

        assertThat(result).extracting(FileAttachment::getOriginalName)
                .containsExactly("m1.png", "m2.png");
    }

    @Test
    @DisplayName("findByUser — 본인의 삭제되지 않은 파일만 rowId 내림차순으로 반환(타인 제외)")
    void findByUserReturnsOwnActiveOrderedByRowIdDesc() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 1L, "f1.png");
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 2L, "f2.png");
        persistFile(user, ReferenceType.MEMO_ATTACHMENT, 3L, "f3.png");
        persistFile(other, ReferenceType.MEMO_ATTACHMENT, 4L, "other.png");
        FileAttachment deleted = persistFile(user, ReferenceType.MEMO_ATTACHMENT, 5L, "del.png");
        em.flush();
        deleted.deleteFile();
        em.flush();
        em.clear();

        List<FileAttachment> result = repository.findByUser(user.getRowId());

        assertThat(result).extracting(FileAttachment::getOriginalName)
                .containsExactly("f3.png", "f2.png", "f1.png");
    }
}
