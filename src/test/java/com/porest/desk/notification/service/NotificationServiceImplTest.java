package com.porest.desk.notification.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.repository.NotificationRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 알림 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SseEmitterService sseEmitterService;

    @InjectMocks private NotificationServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Notification othersNotification() {
        Notification n = mock(Notification.class);
        given(n.getUser()).willReturn(user(999L));
        return n;
    }

    @Test
    @DisplayName("markRead — 남의 알림은 읽음 처리 불가")
    void markReadRejectsOthers() {
        Notification n = othersNotification();
        given(notificationRepository.findById(5L)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> sut.markRead(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteNotification — 남의 알림은 삭제 불가")
    void deleteRejectsOthers() {
        Notification n = othersNotification();
        given(notificationRepository.findById(5L)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> sut.deleteNotification(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
