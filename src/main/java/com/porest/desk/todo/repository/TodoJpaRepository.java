package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId, TodoType type) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM Todo t WHERE t.user.rowId = :userRowId AND t.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

        if (type != null) {
            conditions.add(" AND t.type = :type");
        }
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
        if (projectRowId != null) {
            conditions.add(" AND t.project.rowId = :projectRowId");
        }

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY t.sortOrder ASC, t.rowId DESC");

        TypedQuery<Todo> query = entityManager.createQuery(jpql.toString(), Todo.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N);

        if (type != null) {
            query.setParameter("type", type);
        }
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
        if (projectRowId != null) {
            query.setParameter("projectRowId", projectRowId);
        }

        return query.getResultList();
    }

    @Override
    public List<Todo> findByProject(Long projectRowId) {
        return entityManager.createQuery(
            "SELECT t FROM Todo t WHERE t.project.rowId = :projectRowId AND t.isDeleted = :isDeleted ORDER BY t.sortOrder ASC, t.rowId DESC", Todo.class)
            .setParameter("projectRowId", projectRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Todo> findSubtasks(Long parentRowId) {
        return entityManager.createQuery(
            "SELECT t FROM Todo t WHERE t.parent.rowId = :parentRowId AND t.isDeleted = :isDeleted ORDER BY t.sortOrder ASC, t.rowId DESC", Todo.class)
            .setParameter("parentRowId", parentRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
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
    public List<Todo> findDueTodosForReminder(LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery(
            "SELECT t FROM Todo t WHERE t.isDeleted = :isDeleted AND t.status != :completed AND t.dueDate >= :startDate AND t.dueDate <= :endDate ORDER BY t.dueDate ASC", Todo.class)
            .setParameter("isDeleted", YNType.N)
            .setParameter("completed", TodoStatus.COMPLETED)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public Map<Long, int[]> findSubtaskCountsByParentIds(List<Long> parentIds) {
        Map<Long, int[]> result = new HashMap<>();
        if (parentIds.isEmpty()) return result;

        List<Object[]> rows = entityManager.createQuery(
            "SELECT t.parent.rowId, COUNT(t), SUM(CASE WHEN t.status = :completed THEN 1 ELSE 0 END) " +
            "FROM Todo t WHERE t.parent.rowId IN :parentIds AND t.isDeleted = :isDeleted GROUP BY t.parent.rowId", Object[].class)
            .setParameter("parentIds", parentIds)
            .setParameter("completed", TodoStatus.COMPLETED)
            .setParameter("isDeleted", YNType.N)
            .getResultList();

        for (Object[] row : rows) {
            Long parentId = (Long) row[0];
            int total = ((Number) row[1]).intValue();
            int completed = ((Number) row[2]).intValue();
            result.put(parentId, new int[]{total, completed});
        }

        return result;
    }

    @Override
    public long[] countStatsByUser(Long userRowId, LocalDate today) {
        Object[] row = entityManager.createQuery(
            "SELECT " +
            "SUM(CASE WHEN t.type = :task THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :task AND t.status = :pending THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :task AND t.status = :inProgress THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :task AND t.status = :completed THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :task AND t.dueDate = :today THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :task AND t.dueDate < :today AND t.status != :completed THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :note THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.type = :note AND t.isPinned = :yes THEN 1 ELSE 0 END) " +
            "FROM Todo t WHERE t.user.rowId = :userRowId AND t.isDeleted = :isDeleted AND t.parent IS NULL", Object[].class)
            .setParameter("task", TodoType.TASK)
            .setParameter("note", TodoType.NOTE)
            .setParameter("pending", TodoStatus.PENDING)
            .setParameter("inProgress", TodoStatus.IN_PROGRESS)
            .setParameter("completed", TodoStatus.COMPLETED)
            .setParameter("today", today)
            .setParameter("yes", com.porest.core.type.YNType.Y)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();

        if (row == null) {
            return new long[]{0, 0, 0, 0, 0, 0, 0, 0};
        }

        long[] stats = new long[8];
        for (int i = 0; i < 8; i++) {
            Number val = (Number) row[i];
            stats[i] = val != null ? val.longValue() : 0;
        }
        return stats;
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
