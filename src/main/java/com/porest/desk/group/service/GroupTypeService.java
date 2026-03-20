package com.porest.desk.group.service;

import com.porest.desk.group.service.dto.GroupTypeServiceDto;

import java.util.List;

public interface GroupTypeService {
    GroupTypeServiceDto.GroupTypeInfo createGroupType(GroupTypeServiceDto.CreateCommand command);
    List<GroupTypeServiceDto.GroupTypeInfo> getGroupTypes(Long userRowId);
    GroupTypeServiceDto.GroupTypeInfo updateGroupType(GroupTypeServiceDto.UpdateCommand command);
    void deleteGroupType(Long groupTypeId, Long userRowId);
}
