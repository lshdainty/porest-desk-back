package com.porest.desk.group.service;

import com.porest.desk.group.service.dto.EventCommentServiceDto;

import java.util.List;

public interface EventCommentService {
    EventCommentServiceDto.CommentInfo createComment(EventCommentServiceDto.CreateCommand command);
    List<EventCommentServiceDto.CommentInfo> getComments(Long eventRowId);
    EventCommentServiceDto.CommentInfo updateComment(EventCommentServiceDto.UpdateCommand command);
    void deleteComment(Long commentRowId);
}
