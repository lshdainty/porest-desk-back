package com.porest.desk.file.repository;

import com.porest.core.type.YNType;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.domain.QFileAttachment;
import com.porest.desk.file.type.ReferenceType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class FileAttachmentQueryDslRepository implements FileAttachmentRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QFileAttachment file = QFileAttachment.fileAttachment;

    @Override
    public Optional<FileAttachment> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(file)
                .where(file.rowId.eq(rowId), file.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<FileAttachment> findByReference(ReferenceType referenceType, Long referenceRowId) {
        return queryFactory.selectFrom(file)
            .where(
                file.referenceType.eq(referenceType),
                file.referenceRowId.eq(referenceRowId),
                file.isDeleted.eq(YNType.N)
            )
            .orderBy(file.rowId.asc())
            .fetch();
    }

    @Override
    public List<FileAttachment> findByUser(Long userRowId) {
        return queryFactory.selectFrom(file)
            .where(
                file.user.rowId.eq(userRowId),
                file.isDeleted.eq(YNType.N)
            )
            .orderBy(file.rowId.desc())
            .fetch();
    }

    @Override
    public FileAttachment save(FileAttachment entity) {
        entityManager.persist(entity);
        return entity;
    }
}
