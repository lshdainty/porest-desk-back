package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.QTodoTagMapping;
import com.porest.desk.todo.domain.TodoTagMapping;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class TodoTagMappingQueryDslRepository implements TodoTagMappingRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodoTagMapping mapping = QTodoTagMapping.todoTagMapping;

    @Override
    public List<TodoTagMapping> findByTodoId(Long todoRowId) {
        return queryFactory.selectFrom(mapping)
            .where(mapping.todo.rowId.eq(todoRowId))
            .fetch();
    }

    @Override
    public List<TodoTagMapping> findByTodoIds(List<Long> todoRowIds) {
        return queryFactory.selectFrom(mapping)
            .where(mapping.todo.rowId.in(todoRowIds))
            .fetch();
    }

    @Override
    public TodoTagMapping save(TodoTagMapping entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void deleteByTodoId(Long todoRowId) {
        queryFactory.delete(mapping)
            .where(mapping.todo.rowId.eq(todoRowId))
            .execute();
    }
}
