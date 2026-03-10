package com.porest.desk.group.repository;

import com.porest.desk.group.domain.GroupType;

import java.util.List;
import java.util.Optional;

public interface GroupTypeRepository {
    Optional<GroupType> findById(Long rowId);
    List<GroupType> findAllByUser(Long userRowId);
    GroupType save(GroupType groupType);
}
