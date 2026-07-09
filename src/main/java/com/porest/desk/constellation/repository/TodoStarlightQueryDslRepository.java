package com.porest.desk.constellation.repository;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.QTodoStarlight;
import com.porest.desk.constellation.domain.TodoStarlight;
import com.porest.desk.constellation.type.StarlightSourceType;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class TodoStarlightQueryDslRepository implements TodoStarlightRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodoStarlight starlight = QTodoStarlight.todoStarlight;

    @Override
    public boolean existsBySourceIncludingRevoked(StarlightSourceType sourceType, Long sourceRowId) {
        // 평생 1회 정책 — 회수(soft delete)된 행도 이력으로 취급하므로 is_deleted 필터 없음.
        return queryFactory.selectOne()
            .from(starlight)
            .where(starlight.sourceType.eq(sourceType), starlight.sourceRowId.eq(sourceRowId))
            .fetchFirst() != null;
    }

    @Override
    public Optional<TodoStarlight> findActiveBySource(StarlightSourceType sourceType, Long sourceRowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(starlight)
                .where(
                    starlight.sourceType.eq(sourceType),
                    starlight.sourceRowId.eq(sourceRowId),
                    starlight.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public long countActiveMemoEarns(Long userRowId, LocalDate earnDate) {
        Long count = queryFactory.select(starlight.count())
            .from(starlight)
            .where(
                starlight.user.rowId.eq(userRowId),
                starlight.sourceType.eq(StarlightSourceType.MEMO),
                starlight.earnDate.eq(earnDate),
                starlight.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public Map<StarlightSourceType, Integer> sumActivePointsByDate(Long userRowId, LocalDate earnDate) {
        // 하루 최대 수십 건 — 코드베이스 관례대로 행을 가져와 자바에서 합산
        List<Tuple> rows = queryFactory
            .select(starlight.sourceType, starlight.points)
            .from(starlight)
            .where(
                starlight.user.rowId.eq(userRowId),
                starlight.earnDate.eq(earnDate),
                starlight.isDeleted.eq(YNType.N)
            )
            .fetch();

        Map<StarlightSourceType, Integer> result = new EnumMap<>(StarlightSourceType.class);
        for (Tuple row : rows) {
            StarlightSourceType type = row.get(starlight.sourceType);
            Integer points = row.get(starlight.points);
            if (type != null) {
                result.merge(type, points != null ? points : 0, Integer::sum);
            }
        }
        return result;
    }

    @Override
    public TodoStarlight save(TodoStarlight entity) {
        entityManager.persist(entity);
        return entity;
    }
}
