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
            "SELECT m FROM Memo m LEFT JOIN FETCH m.memoTag WHERE m.rowId = :rowId AND m.isDeleted = :isDeleted", Memo.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<Memo> findAllByUser(Long userRowId, String search) {
        StringBuilder jpql = new StringBuilder(
            "SELECT m FROM Memo m LEFT JOIN FETCH m.memoTag WHERE m.user.rowId = :userRowId AND m.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

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

        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }

        return query.getResultList();
    }

    /**
     * QueryDSL 쪽과 같은 규칙 — FK 로 이어진 행과 이름만 같은 행을 함께 옮긴다.
     * 벌크 UPDATE 라 영속성 컨텍스트를 거치지 않는 것도 같다.
     */
    @Override
    public long renameTag(Long userRowId, Long tagRowId, String fromTagName, String toTagName) {
        return entityManager.createQuery(
            "UPDATE Memo m SET m.tag = :toTagName"
                + " WHERE m.user.rowId = :userRowId AND m.isDeleted = :isDeleted"
                + " AND (m.memoTag.rowId = :tagRowId OR m.tag = :fromTagName)")
            .setParameter("toTagName", toTagName)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("tagRowId", tagRowId)
            .setParameter("fromTagName", fromTagName)
            .executeUpdate();
    }

    @Override
    public long clearTag(Long userRowId, Long tagRowId, String tagName) {
        return entityManager.createQuery(
            "UPDATE Memo m SET m.tag = NULL, m.memoTag = NULL"
                + " WHERE m.user.rowId = :userRowId AND m.isDeleted = :isDeleted"
                + " AND (m.memoTag.rowId = :tagRowId OR m.tag = :tagName)")
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("tagRowId", tagRowId)
            .setParameter("tagName", tagName)
            .executeUpdate();
    }

    @Override
    public Memo save(Memo entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(Memo entity) {
        entity.deleteMemo();
    }
}
