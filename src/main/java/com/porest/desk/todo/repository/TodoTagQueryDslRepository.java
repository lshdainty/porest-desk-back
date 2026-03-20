package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodoTag;
import com.porest.desk.todo.domain.TodoTag;
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
public class TodoTagQueryDslRepository implements TodoTagRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodoTag tag = QTodoTag.todoTag;

    @Override
    public Optional<TodoTag> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(tag)
                .where(tag.rowId.eq(rowId), tag.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<TodoTag> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(tag)
            .where(tag.user.rowId.eq(userRowId), tag.isDeleted.eq(YNType.N))
            .orderBy(tag.tagName.asc())
            .fetch();
    }

    @Override
    public List<TodoTag> findAllByIds(List<Long> ids) {
        return queryFactory.selectFrom(tag)
            .where(tag.rowId.in(ids), tag.isDeleted.eq(YNType.N))
            .fetch();
    }

    @Override
    public TodoTag save(TodoTag entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(TodoTag entity) {
        entity.deleteTag();
    }
}
