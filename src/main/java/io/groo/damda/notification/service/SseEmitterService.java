package io.groo.damda.notification.service;

import io.groo.damda.notification.service.dto.NotificationServiceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자당 여러 SSE 연결(탭 · 기기)을 둔다. 새 구독이 옛 구독을 끊지 않는다 —
 * 예전엔 사용자당 emitter 하나라 탭 둘이 약 1초 간격으로 서로를 끊었다.
 * 사용자당 상한({@link #MAX_EMITTERS_PER_USER})을 넘으면 가장 오래된 연결만 닫는다.
 */
@Service
@Slf4j
public class SseEmitterService {
    static final int MAX_EMITTERS_PER_USER = 5;
    /** 상한 초과로 밀려날 때 그 연결에 보내는 이벤트 이름. 웹은 받으면 재연결을 멈춘다. */
    static final String REPLACED_EVENT = "REPLACED";
    private static final long TIMEOUT_MS = 1800000L;

    /** 사용자별 연결 목록(오래된 순). 목록은 불변 복사본으로 교체한다(compute 안에서만). */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    public SseEmitter subscribe(Long userRowId) {
        SseEmitter emitter = newEmitter();
        List<SseEmitter> evicted = new ArrayList<>();

        emitters.compute(userRowId, (id, current) -> {
            List<SseEmitter> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            next.add(emitter);
            while (next.size() > MAX_EMITTERS_PER_USER) {
                evicted.add(next.remove(0)); // 가장 오래된 것
            }
            return List.copyOf(next);
        });

        // 콜백은 그 emitter 만 제거한다(다른 연결과 새 연결은 건드리지 않는다)
        emitter.onCompletion(() -> remove(userRowId, emitter));
        emitter.onTimeout(() -> remove(userRowId, emitter));
        emitter.onError(e -> remove(userRowId, emitter));

        for (SseEmitter old : evicted) {
            log.info("SSE 상한 초과, 가장 오래된 연결 종료: userRowId={}", userRowId);
            // 밀려난 탭이 곧바로 다시 붙어 다음 탭을 밀어내는 순환을 막는다 — 다시 잇지 말라고 알리고 닫는다
            try {
                old.send(SseEmitter.event().name(REPLACED_EVENT).data("replaced"));
            } catch (Exception ignored) {
            }
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }

        try {
            emitter.send(SseEmitter.event()
                .name("CONNECT")
                .data("connected"));
        } catch (Exception e) {
            log.error("SSE 초기 연결 이벤트 전송 실패: userRowId={}", userRowId, e);
            remove(userRowId, emitter);
        }

        log.info("SSE 구독 연결: userRowId={}", userRowId);
        return emitter;
    }

    public void sendNotification(Long userRowId, NotificationServiceDto.NotificationInfo notification) {
        List<SseEmitter> targets = emitters.get(userRowId);
        if (targets == null) {
            return;
        }

        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                    .name("NOTIFICATION")
                    .data(notification, MediaType.APPLICATION_JSON));
                log.debug("SSE 알림 전송: userRowId={}", userRowId);
            } catch (Exception e) {
                // 한 연결의 실패가 같은 사용자의 다른 연결로 번지지 않는다
                log.error("SSE 알림 전송 실패: userRowId={}", userRowId, e);
                remove(userRowId, emitter);
            }
        }
    }

    public void sendHeartbeat() {
        emitters.forEach((userRowId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception e) {
                    log.debug("SSE 하트비트 전송 실패, 연결 제거: userRowId={}", userRowId);
                    remove(userRowId, emitter);
                }
            }
        });
    }

    /** 그 emitter 하나만 뺀다. 사용자의 마지막 연결이면 항목도 지운다. */
    void remove(Long userRowId, SseEmitter emitter) {
        emitters.computeIfPresent(userRowId, (id, current) -> {
            List<SseEmitter> next = new ArrayList<>(current);
            next.removeIf(e -> e == emitter);
            return next.isEmpty() ? null : List.copyOf(next);
        });
    }

    int emitterCount(Long userRowId) {
        List<SseEmitter> list = emitters.get(userRowId);
        return list == null ? 0 : list.size();
    }
}
