package com.porest.desk.common.audit;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.porest.desk.common.audit.QAccessLog.accessLog;

/**
 * AccessLog QueryDSL 구현체 ({@code @Primary}).
 *
 * @see AccessLogRepository 인터페이스
 * @see AccessLogJpaRepository JPQL 구현체 (백업용)
 */
@Repository
@Primary
@RequiredArgsConstructor
public class AccessLogQueryDslRepository implements AccessLogRepository {

    private final EntityManager em;
    private final JPAQueryFactory query;

    @Override
    public void save(AccessLog log) {
        em.persist(log);
    }

    @Override
    public List<AccessLog> findByActor(String actorId, int limit) {
        return query
                .selectFrom(accessLog)
                .where(accessLog.actorId.eq(actorId))
                .orderBy(accessLog.createAt.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<AccessLog> findByPeriod(LocalDateTime from, LocalDateTime to, int limit) {
        return query
                .selectFrom(accessLog)
                .where(accessLog.createAt.between(from, to))
                .orderBy(accessLog.createAt.desc())
                .limit(limit)
                .fetch();
    }
}
