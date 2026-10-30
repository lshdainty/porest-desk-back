package io.groo.damda.notification.service;

import io.groo.damda.notification.service.dto.NotificationServiceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SseEmitterService - 사용자당 여러 연결")
class SseEmitterServiceTest {

    /** 만든 emitter 를 모두 mock 으로 바꿔 끼운다(complete · send 호출을 센다). */
    private final List<SseEmitter> created = new ArrayList<>();
    private SseEmitterService service;
    private final NotificationServiceDto.NotificationInfo info = mock(NotificationServiceDto.NotificationInfo.class);

    @BeforeEach
    void setUp() {
        created.clear();
        service = new SseEmitterService() {
            @Override
            SseEmitter newEmitter() {
                SseEmitter m = mock(SseEmitter.class);
                created.add(m);
                return m;
            }
        };
    }

    private Runnable completionCallback(SseEmitter emitter) {
        ArgumentCaptor<Runnable> c = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(c.capture());
        return c.getValue();
    }

    private Runnable timeoutCallback(SseEmitter emitter) {
        ArgumentCaptor<Runnable> c = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private Consumer<Throwable> errorCallback(SseEmitter emitter) {
        ArgumentCaptor<Consumer<Throwable>> c = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("같은 사용자의 두 번째 구독이 첫 번째를 끊지 않는다")
    void secondSubscribeDoesNotCompleteFirst() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);

        assertThat(a).isNotSameAs(b);
        verify(a, never()).complete();
        verify(b, never()).complete();
        assertThat(service.emitterCount(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("구독하면 그 연결에 CONNECT 가 한 번 간다")
    void connectEventSentToNewEmitterOnly() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);

        verify(a, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("알림은 같은 사용자의 모든 연결에 가고, 다른 사용자에겐 가지 않는다")
    void notificationGoesToAllOfUserOnly() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        SseEmitter other = service.subscribe(2L);
        clearInvocations(a, b, other);

        service.sendNotification(1L, info);

        verify(a, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(other, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("연결이 없는 사용자에게 보내도 오류 없이 지나간다")
    void sendToUserWithoutEmitter() {
        service.sendNotification(99L, info);
        assertThat(service.emitterCount(99L)).isZero();
    }

    @Test
    @DisplayName("완료 콜백은 그 emitter 만 지우고 다른 연결은 남긴다")
    void completionRemovesOnlyThatEmitter() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        Runnable onDoneA = completionCallback(a);

        onDoneA.run();

        assertThat(service.emitterCount(1L)).isEqualTo(1);
        clearInvocations(a, b);
        service.sendNotification(1L, info);
        verify(a, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("타임아웃 · 오류 콜백도 그 emitter 만 지운다")
    void timeoutAndErrorRemoveOnlyThatEmitter() {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        SseEmitter c = service.subscribe(1L);

        timeoutCallback(a).run();
        assertThat(service.emitterCount(1L)).isEqualTo(2);
        errorCallback(b).accept(new RuntimeException("boom"));
        assertThat(service.emitterCount(1L)).isEqualTo(1);
        verify(c, never()).complete();
    }

    @Test
    @DisplayName("마지막 연결이 끝나면 사용자 항목이 비워진다")
    void lastEmitterRemovalClearsUser() {
        SseEmitter a = service.subscribe(1L);
        completionCallback(a).run();
        assertThat(service.emitterCount(1L)).isZero();
    }

    @Test
    @DisplayName("같은 콜백이 두 번 불려도 다른 연결을 지우지 않는다(멱등)")
    void removalIsIdempotent() {
        SseEmitter a = service.subscribe(1L);
        service.subscribe(1L);
        Runnable done = completionCallback(a);
        done.run();
        done.run();
        assertThat(service.emitterCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("상한 개수까지는 아무도 닫지 않는다")
    void upToLimitNobodyClosed() {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            subs.add(service.subscribe(1L));
        }
        assertThat(service.emitterCount(1L)).isEqualTo(SseEmitterService.MAX_EMITTERS_PER_USER);
        subs.forEach(s -> verify(s, never()).complete());
    }

    @Test
    @DisplayName("상한을 넘으면 가장 오래된 연결만 complete() 된다")
    void overLimitClosesOnlyOldest() {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER + 1; i++) {
            subs.add(service.subscribe(1L));
        }

        verify(subs.get(0), times(1)).complete();
        for (int i = 1; i < subs.size(); i++) {
            verify(subs.get(i), never()).complete();
        }
        assertThat(service.emitterCount(1L)).isEqualTo(SseEmitterService.MAX_EMITTERS_PER_USER);
    }

    @Test
    @DisplayName("상한을 두 번 넘으면 오래된 순서대로 둘이 닫힌다")
    void overLimitTwiceClosesTwoOldest() {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER + 2; i++) {
            subs.add(service.subscribe(1L));
        }
        verify(subs.get(0)).complete();
        verify(subs.get(1)).complete();
        verify(subs.get(2), never()).complete();
    }

    @Test
    @DisplayName("한 사용자의 상한 초과가 다른 사용자의 연결을 닫지 않는다")
    void limitIsPerUser() {
        SseEmitter other = service.subscribe(2L);
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER + 3; i++) {
            service.subscribe(1L);
        }
        verify(other, never()).complete();
        assertThat(service.emitterCount(2L)).isEqualTo(1);
    }

    @Test
    @DisplayName("밀려난 연결의 완료 콜백이 뒤늦게 와도 남은 연결을 지우지 않는다")
    void evictedCallbackDoesNotRemoveSurvivors() {
        SseEmitter oldest = service.subscribe(1L);
        Runnable oldestDone = completionCallback(oldest);
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            service.subscribe(1L);
        }
        oldestDone.run();
        assertThat(service.emitterCount(1L)).isEqualTo(SseEmitterService.MAX_EMITTERS_PER_USER);
    }

    @Test
    @DisplayName("한 연결의 전송 실패는 그 연결만 지우고 나머지엔 계속 보낸다")
    void sendFailureRemovesOnlyFailingEmitter() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        clearInvocations(a, b);
        doThrow(new IOException("broken pipe")).when(a).send(any(SseEmitter.SseEventBuilder.class));

        service.sendNotification(1L, info);

        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.emitterCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 완료된 emitter 가 IllegalStateException 을 내도 전송이 중단되지 않는다")
    void sendToCompletedEmitterDoesNotAbort() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        clearInvocations(a, b);
        doThrow(new IllegalStateException("already completed")).when(a).send(any(SseEmitter.SseEventBuilder.class));

        service.sendNotification(1L, info);

        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.emitterCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("CONNECT 전송이 실패하면 그 연결만 목록에서 빠진다")
    void connectFailureRemovesNewEmitterOnly() throws IOException {
        SseEmitter a = service.subscribe(1L);
        // 다음에 만들어질 emitter 가 send 에서 실패하도록 서비스를 바꿔 끼운다
        SseEmitterService failing = new SseEmitterService() {
            @Override
            SseEmitter newEmitter() {
                SseEmitter m = mock(SseEmitter.class);
                try {
                    doThrow(new IOException("x")).when(m).send(any(SseEmitter.SseEventBuilder.class));
                } catch (IOException ignored) {
                }
                return m;
            }
        };
        failing.subscribe(5L);
        assertThat(failing.emitterCount(5L)).isZero();
        assertThat(service.emitterCount(1L)).isEqualTo(1);
        verify(a, never()).complete();
    }

    @Test
    @DisplayName("하트비트는 모든 연결에 가고, 실패한 연결만 지운다")
    void heartbeatToAllRemovesOnlyFailing() throws IOException {
        SseEmitter a = service.subscribe(1L);
        SseEmitter b = service.subscribe(1L);
        SseEmitter c = service.subscribe(2L);
        clearInvocations(a, b, c);
        doThrow(new IOException("closed")).when(b).send(any(SseEmitter.SseEventBuilder.class));

        service.sendHeartbeat();

        verify(a, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(b, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(c, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.emitterCount(1L)).isEqualTo(1);
        assertThat(service.emitterCount(2L)).isEqualTo(1);
    }

    @Test
    @DisplayName("진짜 SseEmitter 로도 두 구독이 공존하고 알림이 둘 다에 간다(통합 감각)")
    void realEmittersCoexist() {
        SseEmitterService real = new SseEmitterService();
        SseEmitter a = real.subscribe(7L);
        SseEmitter b = real.subscribe(7L);
        assertThat(a).isNotSameAs(b);
        assertThat(real.emitterCount(7L)).isEqualTo(2);
        real.sendNotification(7L, info); // 던지지 않는다
        assertThat(real.emitterCount(7L)).isEqualTo(2);
    }

    @Test
    @DisplayName("상한 초과로 밀려나는 가장 오래된 연결에 REPLACED 를 보낸 뒤 complete() 한다(순서 고정)")
    void evictedGetsReplacedEventBeforeComplete() throws IOException {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            subs.add(service.subscribe(1L));
        }
        SseEmitter oldest = subs.get(0);
        clearInvocations(oldest);

        service.subscribe(1L);

        org.mockito.InOrder order = inOrder(oldest);
        order.verify(oldest).send(any(SseEmitter.SseEventBuilder.class));
        order.verify(oldest).complete();
    }

    @Test
    @DisplayName("REPLACED 는 밀려나는 연결에만 간다 — 남는 연결 · 새 연결은 추가 전송도 complete 도 없다")
    void replacedOnlyToEvicted() throws IOException {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            subs.add(service.subscribe(1L));
        }
        SseEmitter other = service.subscribe(2L);
        subs.forEach(Mockito::clearInvocations);
        clearInvocations(other);

        SseEmitter fresh = service.subscribe(1L);

        verify(subs.get(0), times(1)).send(any(SseEmitter.SseEventBuilder.class));
        for (int i = 1; i < subs.size(); i++) {
            verify(subs.get(i), never()).send(any(SseEmitter.SseEventBuilder.class));
            verify(subs.get(i), never()).complete();
        }
        verify(other, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(fresh, times(1)).send(any(SseEmitter.SseEventBuilder.class)); // CONNECT 만
    }

    @Test
    @DisplayName("상한 이하에서는 REPLACED 를 보내지 않는다(CONNECT 한 번뿐)")
    void noReplacedUnderLimit() throws IOException {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            subs.add(service.subscribe(1L));
        }
        subs.forEach(s -> {
            try {
                verify(s, times(1)).send(any(SseEmitter.SseEventBuilder.class));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @DisplayName("밀려나는 연결의 REPLACED 전송이 실패해도 complete 는 불리고 새 구독은 성공한다")
    void replacedSendFailureStillCompletes() throws IOException {
        List<SseEmitter> subs = new ArrayList<>();
        for (int i = 0; i < SseEmitterService.MAX_EMITTERS_PER_USER; i++) {
            subs.add(service.subscribe(1L));
        }
        doThrow(new IllegalStateException("already completed"))
            .when(subs.get(0)).send(any(SseEmitter.SseEventBuilder.class));

        SseEmitter fresh = service.subscribe(1L);

        verify(subs.get(0)).complete();
        assertThat(fresh).isNotNull();
        assertThat(service.emitterCount(1L)).isEqualTo(SseEmitterService.MAX_EMITTERS_PER_USER);
    }

    @Test
    @DisplayName("이벤트 이름은 REPLACED 로 고정이다(웹과 맞춘 계약)")
    void replacedEventNameContract() {
        assertThat(SseEmitterService.REPLACED_EVENT).isEqualTo("REPLACED");
    }
}
