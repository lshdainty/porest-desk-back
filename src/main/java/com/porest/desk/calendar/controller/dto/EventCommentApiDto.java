package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.EventCommentServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class EventCommentApiDto {

    @Schema(name = "EventCommentCreateRequest")
    public record CreateRequest(
        Long parentRowId,
        String content
    ) {}

    @Schema(name = "EventCommentUpdateRequest")
    public record UpdateRequest(
        String content
    ) {}

    @Schema(name = "EventCommentResponse")
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

    @Schema(name = "EventCommentListResponse")
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
