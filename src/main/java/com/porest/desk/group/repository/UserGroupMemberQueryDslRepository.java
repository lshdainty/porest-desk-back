package com.porest.desk.group.repository;

import com.porest.core.type.YNType;
import com.porest.desk.group.domain.QUserGroupMember;
import com.porest.desk.group.domain.UserGroupMember;
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
public class UserGroupMemberQueryDslRepository implements UserGroupMemberRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QUserGroupMember member = QUserGroupMember.userGroupMember;

    @Override
    public Optional<UserGroupMember> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(member)
                .where(member.rowId.eq(rowId), member.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<UserGroupMember> findByGroupAndUser(Long groupRowId, Long userRowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(member)
                .leftJoin(member.user).fetchJoin()
                .where(
                    member.group.rowId.eq(groupRowId),
                    member.user.rowId.eq(userRowId),
                    member.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public List<UserGroupMember> findAllByGroup(Long groupRowId) {
        return queryFactory.selectFrom(member)
            .leftJoin(member.user).fetchJoin()
            .where(member.group.rowId.eq(groupRowId), member.isDeleted.eq(YNType.N))
            .orderBy(member.joinedAt.asc())
            .fetch();
    }

    @Override
    public List<UserGroupMember> findAllSiblingMembersOfUser(Long userRowId) {
        QUserGroupMember m2 = new QUserGroupMember("m2");
        return queryFactory.selectFrom(member)
            .leftJoin(member.user).fetchJoin()
            .leftJoin(member.group).fetchJoin()
            .where(
                member.isDeleted.eq(YNType.N),
                member.group.rowId.in(
                    queryFactory.select(m2.group.rowId)
                        .from(m2)
                        .where(m2.user.rowId.eq(userRowId), m2.isDeleted.eq(YNType.N))
                )
            )
            .orderBy(member.group.rowId.asc(), member.joinedAt.asc())
            .fetch();
    }

    @Override
    public UserGroupMember save(UserGroupMember entity) {
        entityManager.persist(entity);
        return entity;
    }
}
