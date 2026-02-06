package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository("todoJpaRepository")
@RequiredArgsConstructor
public class TodoJpaRepository implements TodoRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<Todo> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT t FROM Todo t WHERE t.rowId = :rowId AND t.isDeleted = :isDeleted", Todo.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM Todo t WHERE t.user.rowId = :userRowId AND t.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

        if (status != null) {
            conditions.add(" AND t.status = :status");
        }
        if (priority != null) {
            conditions.add(" AND t.priority = :priority");
        }
        if (category != null) {
            conditions.add(" AND t.category = :category");
        }
        if (startDate != null) {
            conditions.add(" AND t.dueDate >= :startDate");
        }
        if (endDate != null) {
            conditions.add(" AND t.dueDate <= :endDate");
        }

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY t.sortOrder ASC, t.rowId DESC");

        TypedQuery<Todo> query = entityManager.createQuery(jpql.toString(), Todo.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N);

        if (status != null) {
            query.setParameter("status", status);
        }
        if (priority != null) {
            query.setParameter("priority", priority);
        }
        if (category != null) {
            query.setParameter("category", category);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }

        return query.getResultList();
    }

    @Override
    public List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery(
            "SELECT t FROM Todo t WHERE t.user.rowId = :userRowId AND t.isDeleted = :isDeleted AND t.dueDate >= :startDate AND t.dueDate <= :endDate ORDER BY t.dueDate ASC, t.sortOrder ASC", Todo.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public Todo save(Todo entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(Todo entity) {
        entity.deleteTodo();
    }
}
