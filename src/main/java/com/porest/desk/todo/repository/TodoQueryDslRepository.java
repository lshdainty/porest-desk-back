package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodo;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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
                .where(todo.rowId.eq(rowId), todo.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(todo.user.rowId.eq(userRowId));
        builder.and(todo.isDeleted.eq(YNType.N));
        builder.and(todo.parent.isNull());

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
            .where(builder)
            .orderBy(todo.sortOrder.asc(), todo.rowId.desc())
            .fetch();
    }

    @Override
    public List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate) {
        return queryFactory.selectFrom(todo)
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
            .where(todo.parent.rowId.eq(parentRowId), todo.isDeleted.eq(YNType.N))
            .orderBy(todo.sortOrder.asc(), todo.rowId.asc())
            .fetch();
    }

    @Override
    public List<Todo> findByProject(Long projectRowId) {
        return queryFactory.selectFrom(todo)
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
