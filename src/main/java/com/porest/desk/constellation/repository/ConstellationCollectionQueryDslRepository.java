package com.porest.desk.constellation.repository;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.ConstellationCollection;
import com.porest.desk.constellation.domain.QConstellationCollection;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class ConstellationCollectionQueryDslRepository implements ConstellationCollectionRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QConstellationCollection collection = QConstellationCollection.constellationCollection;

    @Override
    public List<CollectionStat> findStatsByUser(Long userRowId) {
        List<Tuple> rows = queryFactory
            .select(collection.constellation.rowId, collection.count(), collection.collectedDate.max())
            .from(collection)
            .where(collection.user.rowId.eq(userRowId), collection.isDeleted.eq(YNType.N))
            .groupBy(collection.constellation.rowId)
            .fetch();

        return rows.stream()
            .map(row -> new CollectionStat(
                row.get(collection.constellation.rowId),
                row.get(collection.count()) != null ? row.get(collection.count()) : 0L,
                row.get(collection.collectedDate.max())
            ))
            .toList();
    }

    @Override
    public long countByUser(Long userRowId) {
        Long count = queryFactory.select(collection.count())
            .from(collection)
            .where(collection.user.rowId.eq(userRowId), collection.isDeleted.eq(YNType.N))
            .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public ConstellationCollection save(ConstellationCollection entity) {
        entityManager.persist(entity);
        return entity;
    }
}
