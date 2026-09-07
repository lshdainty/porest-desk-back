package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodoTag;
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
    private static final QTodoTag tag = QTodoTag.todoTag;

    /**
     * 삭제된 태그를 걸러 내는 이유 — <b>태그 삭제는 soft-delete 이고 매핑은 남는다</b>.
     * 거르지 않으면 사용자가 지운 태그가 할 일 응답 {@code tags[]} 에 계속 실린다(QA #79).
     *
     * <p>{@code leftJoin} 이 아니라 {@code join} 인 것은 {@code tag_row_id} 가 NOT NULL 이라
     * 결과가 같기 때문이고, 별칭을 준 것은 {@code where} 가 <b>암묵 조인을 하나 더 만들지</b>
     * 않게 하려는 것이다({@code mapping.tag.isDeleted} 로 쓰면 fetch join 과 별개의 조인이 붙는다).
     */
    @Override
    public List<TodoTagMapping> findByTodoId(Long todoRowId) {
        return queryFactory.selectFrom(mapping)
            .join(mapping.tag, tag).fetchJoin()
            .where(mapping.todo.rowId.eq(todoRowId), tag.isDeleted.eq(YNType.N))
            .fetch();
    }

    @Override
    public List<TodoTagMapping> findByTodoIds(List<Long> todoRowIds) {
        return queryFactory.selectFrom(mapping)
            .join(mapping.tag, tag).fetchJoin()
            .where(mapping.todo.rowId.in(todoRowIds), tag.isDeleted.eq(YNType.N))
            .fetch();
    }

    @Override
    public TodoTagMapping save(TodoTagMapping entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void deleteByTodoId(Long todoRowId) {
        queryFactory.delete(mapping)
            .where(mapping.todo.rowId.eq(todoRowId))
            .execute();
    }

    @Override
    public void deleteByTodoIdAndTagId(Long todoRowId, Long tagRowId) {
        queryFactory.delete(mapping)
            .where(mapping.todo.rowId.eq(todoRowId), mapping.tag.rowId.eq(tagRowId))
            .execute();
    }
}
