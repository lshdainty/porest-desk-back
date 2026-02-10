package com.porest.desk.group.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.group.controller.dto.EventCommentApiDto;
import com.porest.desk.group.service.EventCommentService;
import com.porest.desk.group.service.dto.EventCommentServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventCommentApiController {
    private final EventCommentService eventCommentService;

    @PostMapping("/calendar/event/{eventId}/comment")
    public ApiResponse<EventCommentApiDto.Response> createComment(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long eventId,
            @RequestBody EventCommentApiDto.CreateRequest request) {
        EventCommentServiceDto.CreateCommand command = new EventCommentServiceDto.CreateCommand(
            eventId,
            loginUser.getRowId(),
            request.parentRowId(),
            request.content()
        );
        EventCommentServiceDto.CommentInfo info = eventCommentService.createComment(command);
        return ApiResponse.success(EventCommentApiDto.Response.from(info));
    }

    @GetMapping("/calendar/event/{eventId}/comments")
    public ApiResponse<EventCommentApiDto.ListResponse> getComments(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long eventId) {
        List<EventCommentServiceDto.CommentInfo> infos = eventCommentService.getComments(eventId);
        return ApiResponse.success(EventCommentApiDto.ListResponse.from(infos));
    }

    @PutMapping("/calendar/comment/{commentId}")
    public ApiResponse<EventCommentApiDto.Response> updateComment(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long commentId,
            @RequestBody EventCommentApiDto.UpdateRequest request) {
        EventCommentServiceDto.UpdateCommand command = new EventCommentServiceDto.UpdateCommand(
            commentId,
            request.content()
        );
        EventCommentServiceDto.CommentInfo info = eventCommentService.updateComment(command);
        return ApiResponse.success(EventCommentApiDto.Response.from(info));
    }

    @DeleteMapping("/calendar/comment/{commentId}")
    public ApiResponse<Void> deleteComment(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long commentId) {
        eventCommentService.deleteComment(commentId);
        return ApiResponse.success();
    }
}
