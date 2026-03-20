package com.porest.desk.notification.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.notification.controller.dto.NotificationApiDto;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.SseEmitterService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationApiController {
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@LoginUser UserPrincipal loginUser) {
        return sseEmitterService.subscribe(loginUser.getRowId());
    }

    @GetMapping("/notifications")
    public ApiResponse<NotificationApiDto.ListResponse> getNotifications(
            @LoginUser UserPrincipal loginUser) {
        List<NotificationServiceDto.NotificationInfo> infos =
            notificationService.getNotifications(loginUser.getRowId());
        return ApiResponse.success(NotificationApiDto.ListResponse.from(infos));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<NotificationApiDto.UnreadCountResponse> getUnreadCount(
            @LoginUser UserPrincipal loginUser) {
        Long count = notificationService.getUnreadCount(loginUser.getRowId());
        return ApiResponse.success(new NotificationApiDto.UnreadCountResponse(count));
    }

    @PatchMapping("/notification/{id}/read")
    public ApiResponse<Void> markRead(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        notificationService.markRead(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    @PatchMapping("/notifications/read-all")
    public ApiResponse<Void> markAllRead(
            @LoginUser UserPrincipal loginUser) {
        notificationService.markAllRead(loginUser.getRowId());
        return ApiResponse.success();
    }

    @DeleteMapping("/notification/{id}")
    public ApiResponse<Void> deleteNotification(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        notificationService.deleteNotification(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
