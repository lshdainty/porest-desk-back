package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodo;
import com.porest.desk.todo.domain.QTodoTag;
import com.porest.desk.todo.domain.QTodoTagMapping;
import com.porest.desk.todo.domain.TodoTag;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class TodoTagQueryDslRepository implements TodoTagRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodoTag tag = QTodoTag.todoTag;
    private static final QTodoTagMapping mapping = QTodoTagMapping.todoTagMapping;
    private static final QTodo todo = QTodo.todo;

    @Override
    public Optional<TodoTag> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(tag)
                .where(tag.rowId.eq(rowId), tag.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public TodoTag getReference(Long rowId) {
        return entityManager.getReference(TodoTag.class, rowId);
    }

    @Override
    public List<TodoTag> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(tag)
            .where(tag.user.rowId.eq(userRowId), tag.isDeleted.eq(YNType.N))
            .orderBy(tag.tagName.asc())
            .fetch();
    }

    @Override
    public boolean existsActiveByUserAndName(Long userRowId, String tagName, Long excludeRowId) {
        return queryFactory.selectOne()
            .from(tag)
            .where(
                tag.user.rowId.eq(userRowId),
                tag.tagName.eq(tagName),
                tag.isDeleted.eq(YNType.N),
                excludeRowId != null ? tag.rowId.ne(excludeRowId) : null
            )
            .fetchFirst() != null;
    }

    /**
     * {@code fetchOne} 이 아니라 {@code fetchFirst} 인 이유 — UK 는 <b>활성 행 사이에서만</b>
     * 유일하다(생성 컬럼 {@code active_tag_name}). 지금 스키마로는 활성 동명이 둘일 수 없지만,
     * 여기서 예외가 나면 할 일 저장 전체가 죽으므로 하나를 골라 쓴다.
     */
    @Override
    public Optional<TodoTag> findActiveByUserAndName(Long userRowId, String tagName) {
        return Optional.ofNullable(
            queryFactory.selectFrom(tag)
                .where(
                    tag.user.rowId.eq(userRowId),
                    tag.tagName.eq(tagName),
                    tag.isDeleted.eq(YNType.N)
                )
                .orderBy(tag.rowId.asc())
                .fetchFirst()
        );
    }

    @Override
    public List<TodoTag> findAllByIds(List<Long> ids) {
        return queryFactory.selectFrom(tag)
            .where(tag.rowId.in(ids), tag.isDeleted.eq(YNType.N))
            .fetch();
    }

    /**
     * 라벨이 이미 쓰던 모양({@code EventLabelQueryDslRepository.countEventsByLabel})을 그대로
     * 가져오되 <b>이름이 아니라 FK 로</b> 센다. 이름으로 세면 태그를 개명하는 순간 집계가 0 이 된다.
     *
     * <p>모수는 <b>이 태그가 붙은 살아 있는 할 일 전부</b>다 — 서브태스크도 NOTE 도 함께 센다.
     * 목록 조회({@code findAllByUser})가 최상위만 보는 것과 일부러 다르다: 이 숫자가 나가는
     * 자리는 삭제 확인창의 "이 태그를 쓰는 할 일 N건은 태그 없음으로 남아요" 이고, 서브태스크에
     * 붙은 태그도 똑같이 사라지기 때문이다. 빼면 <b>말한 적 없는 행에서 태그가 사라진다</b>.
     *
     * <p>{@code mapping.tag.rowId} 는 조인 없이 FK 컬럼을 그대로 읽는다. 할 일 쪽은
     * 주인·삭제 여부를 봐야 해서 조인이 하나 필요하다.
     */
    @Override
    public Map<Long, Long> countTodosByTag(Long userRowId) {
        return queryFactory
            .select(mapping.tag.rowId, mapping.count())
            .from(mapping)
            .join(mapping.todo, todo)
            .where(
                todo.user.rowId.eq(userRowId),
                todo.isDeleted.eq(YNType.N)
            )
            .groupBy(mapping.tag.rowId)
            .fetch()
            .stream()
            .collect(Collectors.toMap(
                t -> t.get(mapping.tag.rowId),
                t -> t.get(mapping.count()) == null ? 0L : t.get(mapping.count())
            ));
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

    @Override
    public void flush() {
        entityManager.flush();
    }
}
