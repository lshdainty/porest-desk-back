package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.EventCommentServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class EventCommentApiDto {

    /**
     * {@code event_comment.content} 는 NOT NULL(varchar 1000). 빈 댓글은 저장할 것도 없는데
     * 종전엔 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다(QA #81). 길이도 여기서 끊는다 —
     * 컬럼 폭을 넘기면 그것 역시 DB 까지 가서 터졌다.
     */
    @Schema(name = "EventCommentCreateRequest")
    public record CreateRequest(
        Long parentRowId,
        @NotBlank(message = "댓글 내용을 입력해 주세요")
        @Size(max = 1000, message = "댓글은 1,000자까지 입력할 수 있어요")
        String content
    ) {}

    @Schema(name = "EventCommentUpdateRequest")
    public record UpdateRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요")
        @Size(max = 1000, message = "댓글은 1,000자까지 입력할 수 있어요")
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
