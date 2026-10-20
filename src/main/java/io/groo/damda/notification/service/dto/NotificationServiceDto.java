package io.groo.damda.notification.service.dto;

import io.groo.core.type.YNType;
import io.groo.damda.notification.domain.Notification;
import io.groo.damda.notification.type.NotificationType;
import io.groo.damda.notification.type.ReferenceType;

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
