package com.porest.desk.notification.repository;

import com.porest.core.type.YNType;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.domain.QNotification;
import com.porest.desk.notification.type.ReferenceType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class NotificationQueryDslRepository implements NotificationRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QNotification notification = QNotification.notification;

    @Override
    public Optional<Notification> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(notification)
                .where(notification.rowId.eq(rowId), notification.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Notification> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(notification)
            .where(notification.user.rowId.eq(userRowId), notification.isDeleted.eq(YNType.N))
            .orderBy(notification.createAt.desc())
            .limit(100)
            .fetch();
    }

    @Override
    public Long countUnread(Long userRowId) {
        return queryFactory.select(notification.count())
            .from(notification)
            .where(
                notification.user.rowId.eq(userRowId),
                notification.isRead.eq(YNType.N),
                notification.isDeleted.eq(YNType.N)
            )
            .fetchOne();
    }

    @Override
    public boolean existsByUserAndReferenceAndCreatedAfter(Long userRowId, ReferenceType referenceType, Long referenceId, LocalDateTime after) {
        return queryFactory.selectFrom(notification)
            .where(
                notification.user.rowId.eq(userRowId),
                notification.referenceType.eq(referenceType),
                notification.referenceId.eq(referenceId),
                notification.isDeleted.eq(YNType.N),
                notification.createAt.goe(after)
            )
            .fetchFirst() != null;
    }

    @Override
    public Notification save(Notification entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void markAllRead(Long userRowId) {
        queryFactory.update(notification)
            .set(notification.isRead, YNType.Y)
            .set(notification.readAt, LocalDateTime.now())
            .where(
                notification.user.rowId.eq(userRowId),
                notification.isRead.eq(YNType.N),
                notification.isDeleted.eq(YNType.N)
            )
            .execute();
    }
}
