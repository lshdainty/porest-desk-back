package io.groo.damda.notification.repository;

import io.groo.damda.notification.domain.Notification;
import io.groo.damda.notification.type.ReferenceType;

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
