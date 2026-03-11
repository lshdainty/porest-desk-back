package com.porest.desk.notification.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.repository.NotificationRepository;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;

    @Override
    @Transactional
    public NotificationServiceDto.NotificationInfo createNotification(NotificationServiceDto.CreateCommand command) {
        log.debug("알림 생성: userRowId={}, type={}", command.userRowId(), command.notificationType());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.createNotification(
            user,
            command.notificationType(),
            command.title(),
            command.message(),
            command.referenceType(),
            command.referenceId()
        );

        notificationRepository.save(notification);
        log.info("알림 생성 완료: notificationId={}", notification.getRowId());

        NotificationServiceDto.NotificationInfo info = NotificationServiceDto.NotificationInfo.from(notification);
        sseEmitterService.sendNotification(command.userRowId(), info);

        return info;
    }

    @Override
    public List<NotificationServiceDto.NotificationInfo> getNotifications(Long userRowId) {
        log.debug("알림 목록 조회: userRowId={}", userRowId);

        return notificationRepository.findAllByUser(userRowId).stream()
            .map(NotificationServiceDto.NotificationInfo::from)
            .toList();
    }

    @Override
    public Long getUnreadCount(Long userRowId) {
        return notificationRepository.countUnread(userRowId);
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, Long userRowId) {
        log.debug("알림 읽음 처리: notificationId={}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.NOTIFICATION_NOT_FOUND));
        validateNotificationOwnership(notification, userRowId);

        notification.markRead();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(Long userRowId) {
        log.debug("알림 전체 읽음 처리: userRowId={}", userRowId);
        notificationRepository.markAllRead(userRowId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long userRowId) {
        log.debug("알림 삭제: notificationId={}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.NOTIFICATION_NOT_FOUND));
        validateNotificationOwnership(notification, userRowId);

        notification.deleteNotification();
        notificationRepository.save(notification);
    }

    private void validateNotificationOwnership(Notification notification, Long userRowId) {
        if (!notification.getUser().getRowId().equals(userRowId)) {
            log.warn("알림 소유권 검증 실패 - notificationId={}, ownerRowId={}, requestUserRowId={}",
                notification.getRowId(), notification.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
