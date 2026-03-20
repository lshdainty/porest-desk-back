package com.porest.desk.notification.repository;

import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.type.ReferenceType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Optional<Notification> findById(Long rowId);
    List<Notification> findAllByUser(Long userRowId);
    Long countUnread(Long userRowId);
    boolean existsByUserAndReferenceAndCreatedAfter(Long userRowId, ReferenceType referenceType, Long referenceId, LocalDateTime after);
    Notification save(Notification notification);
    void markAllRead(Long userRowId);
}
