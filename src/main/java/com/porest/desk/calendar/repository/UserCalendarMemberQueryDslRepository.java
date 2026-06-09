package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.QUserCalendarMember;
import com.porest.desk.calendar.domain.UserCalendarMember;
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
public class UserCalendarMemberQueryDslRepository implements UserCalendarMemberRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QUserCalendarMember member = QUserCalendarMember.userCalendarMember;

    @Override
    public Optional<UserCalendarMember> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(member)
                .where(member.rowId.eq(rowId), member.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<UserCalendarMember> findByCalendarAndUser(Long calendarRowId, Long userRowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(member)
                .where(
                    member.calendar.rowId.eq(calendarRowId),
                    member.user.rowId.eq(userRowId),
                    member.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public List<UserCalendarMember> findAllByCalendar(Long calendarRowId) {
        return queryFactory.selectFrom(member)
            .where(member.calendar.rowId.eq(calendarRowId), member.isDeleted.eq(YNType.N))
            .orderBy(member.joinedAt.asc())
            .fetch();
    }

    @Override
    public List<Long> findCalendarIdsByUser(Long userRowId) {
        return queryFactory.select(member.calendar.rowId)
            .from(member)
            .where(member.user.rowId.eq(userRowId), member.isDeleted.eq(YNType.N))
            .fetch();
    }

    @Override
    public UserCalendarMember save(UserCalendarMember entity) {
        entityManager.persist(entity);
        return entity;
    }
}
