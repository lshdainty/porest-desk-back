package com.porest.desk.group.service.dto;

import com.porest.desk.group.domain.GroupType;

public class GroupTypeServiceDto {

    public record CreateCommand(
        Long userRowId,
        String typeName,
        String color,
        int sortOrder
    ) {}

    public record UpdateCommand(
        Long groupTypeId,
        Long userRowId,
        String typeName,
        String color,
        int sortOrder
    ) {}

    public record GroupTypeInfo(
        Long rowId,
        String typeName,
        String color,
        int sortOrder
    ) {
        public static GroupTypeInfo from(GroupType entity) {
            return new GroupTypeInfo(
                entity.getRowId(),
                entity.getTypeName(),
                entity.getColor(),
                entity.getSortOrder()
            );
        }
    }
}
