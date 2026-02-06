package com.porest.desk.memo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.Memo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository("memoJpaRepository")
@RequiredArgsConstructor
public class MemoJpaRepository implements MemoRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<Memo> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT m FROM Memo m WHERE m.rowId = :rowId AND m.isDeleted = :isDeleted", Memo.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<Memo> findAllByUser(Long userRowId, Long folderId, String search) {
        StringBuilder jpql = new StringBuilder("SELECT m FROM Memo m WHERE m.user.rowId = :userRowId AND m.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

        if (folderId != null) {
            conditions.add(" AND m.folder.rowId = :folderId");
        }
        if (search != null && !search.isBlank()) {
            conditions.add(" AND (m.title LIKE :search OR m.content LIKE :search)");
        }

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY m.isPinned DESC, m.modifyAt DESC");

        TypedQuery<Memo> query = entityManager.createQuery(jpql.toString(), Memo.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N);

        if (folderId != null) {
            query.setParameter("folderId", folderId);
        }
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }

        return query.getResultList();
    }

    @Override
    public Memo save(Memo entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(Memo entity) {
        entity.deleteMemo();
    }
}
