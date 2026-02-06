package com.porest.desk.memo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.MemoFolder;
import com.porest.desk.memo.domain.QMemoFolder;
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
public class MemoFolderQueryDslRepository implements MemoFolderRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QMemoFolder memoFolder = QMemoFolder.memoFolder;

    @Override
    public Optional<MemoFolder> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(memoFolder)
                .where(memoFolder.rowId.eq(rowId), memoFolder.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<MemoFolder> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(memoFolder)
            .where(
                memoFolder.user.rowId.eq(userRowId),
                memoFolder.isDeleted.eq(YNType.N)
            )
            .orderBy(memoFolder.sortOrder.asc(), memoFolder.rowId.asc())
            .fetch();
    }

    @Override
    public MemoFolder save(MemoFolder entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(MemoFolder entity) {
        entity.deleteFolder();
    }
}
