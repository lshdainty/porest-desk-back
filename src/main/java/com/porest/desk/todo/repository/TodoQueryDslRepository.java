package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodo;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class TodoQueryDslRepository implements TodoRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodo todo = QTodo.todo;

    @Override
    public Optional<Todo> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(todo)
                .leftJoin(todo.user).fetchJoin()
                .leftJoin(todo.project).fetchJoin()
                .where(todo.rowId.eq(rowId), todo.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId, TodoType type) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(todo.user.rowId.eq(userRowId));
        builder.and(todo.isDeleted.eq(YNType.N));
        builder.and(todo.parent.isNull());

        if (type != null) {
            builder.and(todo.type.eq(type));
        }
        if (status != null) {
            builder.and(todo.status.eq(status));
        }
        if (priority != null) {
            builder.and(todo.priority.eq(priority));
        }
        if (category != null) {
            builder.and(todo.category.eq(category));
        }
        if (startDate != null) {
            builder.and(todo.dueDate.goe(startDate));
        }
        if (endDate != null) {
            builder.and(todo.dueDate.loe(endDate));
        }
        if (projectRowId != null) {
            builder.and(todo.project.rowId.eq(projectRowId));
        }

        return queryFactory.selectFrom(todo)
            .leftJoin(todo.user).fetchJoin()
            .leftJoin(todo.project).fetchJoin()
            .where(builder)
            .orderBy(todo.sortOrder.asc(), todo.rowId.desc())
            .fetch();
    }

    @Override
    public List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate) {
        return queryFactory.selectFrom(todo)
            .leftJoin(todo.user).fetchJoin()
            .leftJoin(todo.project).fetchJoin()
            .where(
                todo.user.rowId.eq(userRowId),
                todo.isDeleted.eq(YNType.N),
                todo.dueDate.goe(startDate),
                todo.dueDate.loe(endDate)
            )
            .orderBy(todo.dueDate.asc(), todo.sortOrder.asc())
            .fetch();
    }

    @Override
    public List<Todo> findSubtasks(Long parentRowId) {
        return queryFactory.selectFrom(todo)
            .leftJoin(todo.user).fetchJoin()
            .leftJoin(todo.project).fetchJoin()
            .where(todo.parent.rowId.eq(parentRowId), todo.isDeleted.eq(YNType.N))
            .orderBy(todo.sortOrder.asc(), todo.rowId.asc())
            .fetch();
    }

    @Override
    public Map<Long, int[]> findSubtaskCountsByParentIds(List<Long> parentIds) {
        Map<Long, int[]> result = new HashMap<>();
        if (parentIds.isEmpty()) return result;

        NumberTemplate<Long> completedCount = Expressions.numberTemplate(Long.class,
            "SUM(CASE WHEN {0} = {1} THEN 1 ELSE 0 END)", todo.status, TodoStatus.COMPLETED);

        List<Tuple> tuples = queryFactory
            .select(
                todo.parent.rowId,
                todo.count(),
                completedCount
            )
            .from(todo)
            .where(todo.parent.rowId.in(parentIds), todo.isDeleted.eq(YNType.N))
            .groupBy(todo.parent.rowId)
            .fetch();

        for (Tuple tuple : tuples) {
            Long parentId = tuple.get(0, Long.class);
            Long total = tuple.get(1, Long.class);
            Long completed = tuple.get(2, Long.class);
            result.put(parentId, new int[]{
                total != null ? total.intValue() : 0,
                completed != null ? completed.intValue() : 0
            });
        }

        return result;
    }

    @Override
    public long[] countStatsByUser(Long userRowId, LocalDate today) {
        Tuple result = queryFactory
            .select(
                // 0: totalTask
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} THEN 1 ELSE 0 END)", todo.type, TodoType.TASK),
                // 1: pending
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} = {3} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.TASK, todo.status, TodoStatus.PENDING),
                // 2: inProgress
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} = {3} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.TASK, todo.status, TodoStatus.IN_PROGRESS),
                // 3: completed
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} = {3} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.TASK, todo.status, TodoStatus.COMPLETED),
                // 4: todayDue
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} = {3} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.TASK, todo.dueDate, today),
                // 5: overDue
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} < {3} AND {4} != {5} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.TASK, todo.dueDate, today, todo.status, TodoStatus.COMPLETED),
                // 6: noteCount
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} THEN 1 ELSE 0 END)", todo.type, TodoType.NOTE),
                // 7: pinnedNoteCount
                Expressions.numberTemplate(Long.class,
                    "SUM(CASE WHEN {0} = {1} AND {2} = {3} THEN 1 ELSE 0 END)",
                    todo.type, TodoType.NOTE, todo.isPinned, YNType.Y)
            )
            .from(todo)
            .where(
                todo.user.rowId.eq(userRowId),
                todo.isDeleted.eq(YNType.N),
                todo.parent.isNull()
            )
            .fetchOne();

        if (result == null) {
            return new long[]{0, 0, 0, 0, 0, 0, 0, 0};
        }

        long[] stats = new long[8];
        for (int i = 0; i < 8; i++) {
            Long val = result.get(i, Long.class);
            stats[i] = val != null ? val : 0;
        }
        return stats;
    }

    @Override
    public List<Todo> findByProject(Long projectRowId) {
        return queryFactory.selectFrom(todo)
            .leftJoin(todo.user).fetchJoin()
            .leftJoin(todo.project).fetchJoin()
            .where(todo.project.rowId.eq(projectRowId), todo.isDeleted.eq(YNType.N), todo.parent.isNull())
            .orderBy(todo.sortOrder.asc(), todo.rowId.desc())
            .fetch();
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
