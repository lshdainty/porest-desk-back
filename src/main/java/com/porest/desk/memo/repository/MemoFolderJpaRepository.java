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
    public boolean existsActiveByUserAndParentAndName(Long userRowId, Long parentRowId, String folderName, Long excludeRowId) {
        StringBuilder jpql = new StringBuilder(
            "SELECT COUNT(f) FROM MemoFolder f WHERE f.user.rowId = :userRowId"
                + " AND f.folderName = :folderName AND f.isDeleted = :isDeleted");
        jpql.append(parentRowId != null ? " AND f.parent.rowId = :parentRowId" : " AND f.parent IS NULL");
        if (excludeRowId != null) {
            jpql.append(" AND f.rowId <> :excludeRowId");
        }
        var query = entityManager.createQuery(jpql.toString(), Long.class)
            .setParameter("userRowId", userRowId)
            .setParameter("folderName", folderName)
            .setParameter("isDeleted", YNType.N);
        if (parentRowId != null) {
            query.setParameter("parentRowId", parentRowId);
        }
        if (excludeRowId != null) {
            query.setParameter("excludeRowId", excludeRowId);
        }
        return query.getSingleResult() > 0;
    }

    @Override
    public MemoFolder save(MemoFolder entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(MemoFolder entity) {
        entity.deleteFolder();
    }
}
