package com.porest.desk.notification.service;

import com.porest.desk.notification.service.dto.NotificationServiceDto;

import java.util.List;

public interface NotificationService {
    NotificationServiceDto.NotificationInfo createNotification(NotificationServiceDto.CreateCommand command);
    List<NotificationServiceDto.NotificationInfo> getNotifications(Long userRowId);
    Long getUnreadCount(Long userRowId);
    void markRead(Long notificationId, Long userRowId);
    void markAllRead(Long userRowId);
    void deleteNotification(Long notificationId, Long userRowId);
}
