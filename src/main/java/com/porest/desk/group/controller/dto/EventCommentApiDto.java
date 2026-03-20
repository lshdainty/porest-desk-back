package com.porest.desk.group.controller.dto;

import com.porest.desk.group.service.dto.EventCommentServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class EventCommentApiDto {

    public record CreateRequest(
        Long parentRowId,
        String content
    ) {}

    public record UpdateRequest(
        String content
    ) {}

    public record Response(
        Long rowId,
        Long eventRowId,
        Long userRowId,
        String userName,
        Long parentRowId,
        String content,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(EventCommentServiceDto.CommentInfo info) {
            return new Response(
                info.rowId(),
                info.eventRowId(),
                info.userRowId(),
                info.userName(),
                info.parentRowId(),
                info.content(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> comments
    ) {
        public static ListResponse from(List<EventCommentServiceDto.CommentInfo> infos) {
            return new ListResponse(
                infos.stream().map(Response::from).toList()
            );
        }
    }
}
