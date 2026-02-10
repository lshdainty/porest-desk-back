package com.porest.desk.group.repository;

import com.porest.desk.group.domain.UserGroupMember;

import java.util.List;
import java.util.Optional;

public interface UserGroupMemberRepository {
    Optional<UserGroupMember> findById(Long rowId);
    Optional<UserGroupMember> findByGroupAndUser(Long groupRowId, Long userRowId);
    List<UserGroupMember> findAllByGroup(Long groupRowId);
    UserGroupMember save(UserGroupMember member);
}
