package com.porest.desk.group.service.dto;

import com.porest.desk.group.domain.EventComment;

import java.time.LocalDateTime;

public class EventCommentServiceDto {

    public record CreateCommand(
        Long eventRowId,
        Long userRowId,
        Long parentRowId,
        String content
    ) {}

    public record UpdateCommand(
        Long commentRowId,
        String content
    ) {}

    public record CommentInfo(
        Long rowId,
        Long eventRowId,
        Long userRowId,
        String userName,
        Long parentRowId,
        String content,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static CommentInfo from(EventComment comment) {
            return new CommentInfo(
                comment.getRowId(),
                comment.getEvent().getRowId(),
                comment.getUser().getRowId(),
                comment.getUser().getUserName(),
                comment.getParent() != null ? comment.getParent().getRowId() : null,
                comment.getContent(),
                comment.getCreateAt(),
                comment.getModifyAt()
            );
        }
    }
}
