package com.porest.desk.notification.repository;

import com.porest.desk.notification.domain.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Optional<Notification> findById(Long rowId);
    List<Notification> findAllByUser(Long userRowId);
    Long countUnread(Long userRowId);
    Notification save(Notification notification);
    void markAllRead(Long userRowId);
}
