package com.porest.desk.group.repository;

import com.porest.desk.group.domain.UserGroup;

import java.util.List;
import java.util.Optional;

public interface UserGroupRepository {
    Optional<UserGroup> findById(Long rowId);
    Optional<UserGroup> findByInviteCode(String inviteCode);
    List<UserGroup> findAllByUser(Long userRowId);
    UserGroup save(UserGroup userGroup);
}
