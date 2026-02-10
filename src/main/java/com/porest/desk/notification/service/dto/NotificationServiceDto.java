package com.porest.desk.notification.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;

import java.time.LocalDateTime;

public class NotificationServiceDto {

    public record CreateCommand(
        Long userRowId,
        NotificationType notificationType,
        String title,
        String message,
        ReferenceType referenceType,
        Long referenceId
    ) {}

    public record NotificationInfo(
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
        public static NotificationInfo from(Notification notification) {
            return new NotificationInfo(
                notification.getRowId(),
                notification.getUser().getRowId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getIsRead() == YNType.Y,
                notification.getReadAt(),
                notification.getCreateAt()
            );
        }
    }
}
