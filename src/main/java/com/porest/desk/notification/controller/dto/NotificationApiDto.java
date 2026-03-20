package com.porest.desk.notification.controller.dto;

import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationApiDto {

    public record Response(
        Long rowId,
        Long userRowId,
        NotificationType notificationType,
        String title,
        String message,
        ReferenceType referenceType,
        Long referenceId,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createAt
    ) {
        public static Response from(NotificationServiceDto.NotificationInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.notificationType(),
                info.title(),
                info.message(),
                info.referenceType(),
                info.referenceId(),
                info.isRead(),
                info.readAt(),
                info.createAt()
            );
        }
    }

    public record ListResponse(
        List<Response> notifications
    ) {
        public static ListResponse from(List<NotificationServiceDto.NotificationInfo> infos) {
            return new ListResponse(
                infos.stream().map(Response::from).toList()
            );
        }
    }

    public record UnreadCountResponse(
        Long count
    ) {}
}
