package com.porest.desk.group.controller.dto;

import com.porest.desk.group.service.dto.GroupTypeServiceDto;

import java.util.List;

public class GroupTypeApiDto {

    public record CreateRequest(
        String typeName,
        String color,
        Integer sortOrder
    ) {}

    public record UpdateRequest(
        String typeName,
        String color,
        Integer sortOrder
    ) {}

    public record Response(
        Long rowId,
        String typeName,
        String color,
        int sortOrder
    ) {
        public static Response from(GroupTypeServiceDto.GroupTypeInfo info) {
            return new Response(
                info.rowId(),
                info.typeName(),
                info.color(),
                info.sortOrder()
            );
        }
    }

    public record ListResponse(
        List<Response> groupTypes
    ) {
        public static ListResponse from(List<GroupTypeServiceDto.GroupTypeInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
