package com.porest.desk.constellation.repository;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.QConstellation;
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
public class ConstellationQueryDslRepository implements ConstellationRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QConstellation constellation = QConstellation.constellation;

    @Override
    public Optional<Constellation> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(constellation)
                .where(constellation.rowId.eq(rowId), constellation.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<Constellation> findByKey(String constellationKey) {
        return Optional.ofNullable(
            queryFactory.selectFrom(constellation)
                .where(constellation.constellationKey.eq(constellationKey), constellation.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Constellation> findAllActive() {
        return queryFactory.selectFrom(constellation)
            .where(constellation.isActive.eq(YNType.Y), constellation.isDeleted.eq(YNType.N))
            .orderBy(constellation.sortOrder.asc(), constellation.rowId.asc())
            .fetch();
    }

    @Override
    public List<Constellation> findAll() {
        return queryFactory.selectFrom(constellation)
            .where(constellation.isDeleted.eq(YNType.N))
            .orderBy(constellation.sortOrder.asc(), constellation.rowId.asc())
            .fetch();
    }

    @Override
    public Constellation save(Constellation entity) {
        entityManager.persist(entity);
        return entity;
    }
}
