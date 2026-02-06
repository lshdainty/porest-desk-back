package com.porest.desk.timer.repository;

import com.porest.core.type.YNType;
import com.porest.desk.timer.domain.QTimerSession;
import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.type.TimerType;
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
public class TimerSessionQueryDslRepository implements TimerSessionRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTimerSession timerSession = QTimerSession.timerSession;

    @Override
    public Optional<TimerSession> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(timerSession)
                .where(timerSession.rowId.eq(rowId))
                .fetchOne()
        );
    }

    @Override
    public List<TimerSession> findByUser(Long userRowId, TimerType timerType, LocalDate startDate, LocalDate endDate) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(timerSession.user.rowId.eq(userRowId));

        if (timerType != null) {
            builder.and(timerSession.timerType.eq(timerType));
        }
        if (startDate != null) {
            builder.and(timerSession.startTime.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            builder.and(timerSession.startTime.lt(endDate.plusDays(1).atStartOfDay()));
        }

        return queryFactory.selectFrom(timerSession)
            .where(builder)
            .orderBy(timerSession.startTime.desc())
            .fetch();
    }

    @Override
    public List<TimerSession> findDailyStats(Long userRowId, LocalDate startDate, LocalDate endDate) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(timerSession.user.rowId.eq(userRowId));
        builder.and(timerSession.isCompleted.eq(YNType.Y));

        if (startDate != null) {
            builder.and(timerSession.startTime.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            builder.and(timerSession.startTime.lt(endDate.plusDays(1).atStartOfDay()));
        }

        return queryFactory.selectFrom(timerSession)
            .where(builder)
            .orderBy(timerSession.startTime.asc())
            .fetch();
    }

    @Override
    public TimerSession save(TimerSession entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(TimerSession entity) {
        entityManager.remove(entity);
    }
}
