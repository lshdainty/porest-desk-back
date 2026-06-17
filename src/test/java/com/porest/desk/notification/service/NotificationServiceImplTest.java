package com.porest.desk.notification.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.notification.domain.Notification;
import com.porest.desk.notification.repository.NotificationRepository;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    // ── 정상 CRUD 결과 정확성 ─────────────────────────────
    @Test
    @DisplayName("createNotification — isRead=false·readAt=null·필드 매핑, SSE 전송")
    void createNotificationDefaultsAndSse() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new NotificationServiceDto.CreateCommand(
                USER_ID, NotificationType.TODO_REMINDER, "할일 알림", "마감 임박", ReferenceType.TODO, 55L);
        NotificationServiceDto.NotificationInfo info = sut.createNotification(cmd);

        assertThat(info.notificationType()).isEqualTo(NotificationType.TODO_REMINDER);
        assertThat(info.title()).isEqualTo("할일 알림");
        assertThat(info.message()).isEqualTo("마감 임박");
        assertThat(info.referenceType()).isEqualTo(ReferenceType.TODO);
        assertThat(info.referenceId()).isEqualTo(55L);
        assertThat(info.isRead()).isFalse();      // 생성 시 미읽음
        assertThat(info.readAt()).isNull();
        verify(sseEmitterService).sendNotification(eq(USER_ID), any());
    }

    @Test
    @DisplayName("markRead — isRead=Y·readAt 세팅, save 호출")
    void markReadSetsReadState() {
        Notification n = Notification.createNotification(user(USER_ID),
                NotificationType.SYSTEM, "t", "m", null, null);
        ReflectionTestUtils.setField(n, "rowId", 301L);
        given(notificationRepository.findById(301L)).willReturn(Optional.of(n));

        sut.markRead(301L, USER_ID);

        assertThat(n.getIsRead()).isEqualTo(YNType.Y);
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("getNotifications — repo 결과 순서·필드·읽음상태 보존 매핑")
    void getNotificationsMapsInOrder() {
        Notification n1 = Notification.createNotification(user(USER_ID),
                NotificationType.BUDGET_ALERT, "예산", "초과", ReferenceType.EXPENSE_BUDGET, 9L);
        ReflectionTestUtils.setField(n1, "rowId", 310L);
        Notification n2 = Notification.createNotification(user(USER_ID),
                NotificationType.EVENT_REMINDER, "일정", "시작", ReferenceType.CALENDAR_EVENT, 7L);
        ReflectionTestUtils.setField(n2, "rowId", 311L);
        n2.markRead(); // 읽음
        given(notificationRepository.findAllByUser(USER_ID)).willReturn(List.of(n1, n2));

        List<NotificationServiceDto.NotificationInfo> result = sut.getNotifications(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rowId()).isEqualTo(310L);
        assertThat(result.get(0).notificationType()).isEqualTo(NotificationType.BUDGET_ALERT);
        assertThat(result.get(0).referenceId()).isEqualTo(9L);
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(1).rowId()).isEqualTo(311L);
        assertThat(result.get(1).isRead()).isTrue();   // n2 는 markRead
    }
}
