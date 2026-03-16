package com.porest.desk.group.repository;

import com.porest.core.type.YNType;
import com.porest.desk.group.domain.QUserGroup;
import com.porest.desk.group.domain.QUserGroupMember;
import com.porest.desk.group.domain.UserGroup;
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
public class UserGroupQueryDslRepository implements UserGroupRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QUserGroup userGroup = QUserGroup.userGroup;
    private static final QUserGroupMember member = QUserGroupMember.userGroupMember;

    @Override
    public Optional<UserGroup> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(userGroup)
                .where(userGroup.rowId.eq(rowId), userGroup.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<UserGroup> findByInviteCode(String inviteCode) {
        return Optional.ofNullable(
            queryFactory.selectFrom(userGroup)
                .where(userGroup.inviteCode.eq(inviteCode), userGroup.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<UserGroup> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(userGroup)
            .join(userGroup.members, member)
            .where(
                member.user.rowId.eq(userRowId),
                member.isDeleted.eq(YNType.N),
                userGroup.isDeleted.eq(YNType.N)
            )
            .orderBy(userGroup.createAt.desc())
            .fetch();
    }

    @Override
    public UserGroup save(UserGroup entity) {
        entityManager.persist(entity);
        return entity;
    }
}
