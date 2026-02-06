package com.porest.desk.memo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.MemoFolder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("memoFolderJpaRepository")
@RequiredArgsConstructor
public class MemoFolderJpaRepository implements MemoFolderRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<MemoFolder> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT f FROM MemoFolder f WHERE f.rowId = :rowId AND f.isDeleted = :isDeleted", MemoFolder.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<MemoFolder> findAllByUser(Long userRowId) {
        return entityManager.createQuery(
            "SELECT f FROM MemoFolder f WHERE f.user.rowId = :userRowId AND f.isDeleted = :isDeleted ORDER BY f.sortOrder ASC, f.rowId ASC", MemoFolder.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
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
