package com.porest.desk.constellation.repository;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.ConstellationDaily;
import com.porest.desk.constellation.domain.QConstellationDaily;
import com.porest.desk.constellation.type.DailyStatus;
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
public class ConstellationDailyQueryDslRepository implements ConstellationDailyRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QConstellationDaily daily = QConstellationDaily.constellationDaily;

    @Override
    public Optional<ConstellationDaily> findByUserAndDate(Long userRowId, LocalDate obsDate) {
        return Optional.ofNullable(
            queryFactory.selectFrom(daily)
                .where(
                    daily.user.rowId.eq(userRowId),
                    daily.obsDate.eq(obsDate),
                    daily.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public List<ConstellationDaily> findByUserAndDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate) {
        // 밤하늘 조회가 행마다 별자리 key/색을 읽으므로 fetch join 으로 N+1 방지
        return queryFactory.selectFrom(daily)
            .leftJoin(daily.constellation).fetchJoin()
            .where(
                daily.user.rowId.eq(userRowId),
                daily.obsDate.between(startDate, endDate),
                daily.isDeleted.eq(YNType.N)
            )
            .orderBy(daily.obsDate.asc())
            .fetch();
    }

    @Override
    public Optional<LocalDate> findLatestGrownDate(Long userRowId) {
        return Optional.ofNullable(
            queryFactory.select(daily.obsDate.max())
                .from(daily)
                .where(
                    daily.user.rowId.eq(userRowId),
                    daily.status.eq(DailyStatus.GROWN),
                    daily.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public ConstellationDaily save(ConstellationDaily entity) {
        entityManager.persist(entity);
        return entity;
    }
}
