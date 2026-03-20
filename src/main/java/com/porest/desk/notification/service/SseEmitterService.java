package com.porest.desk.notification.service;

import com.porest.desk.notification.service.dto.NotificationServiceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService {
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userRowId) {
        // 기존 연결이 있으면 완료 처리
        SseEmitter oldEmitter = emitters.get(userRowId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception ignored) {
            }
        }

        SseEmitter emitter = new SseEmitter(1800000L);
        emitters.put(userRowId, emitter);

        // 콜백에서 현재 emitter와 동일한 경우에만 제거 (race condition 방지)
        emitter.onCompletion(() -> emitters.remove(userRowId, emitter));
        emitter.onTimeout(() -> emitters.remove(userRowId, emitter));
        emitter.onError(e -> emitters.remove(userRowId, emitter));

        try {
            emitter.send(SseEmitter.event()
                .name("CONNECT")
                .data("connected"));
        } catch (IOException e) {
            log.error("SSE 초기 연결 이벤트 전송 실패: userRowId={}", userRowId, e);
            emitters.remove(userRowId, emitter);
        }

        log.info("SSE 구독 연결: userRowId={}", userRowId);
        return emitter;
    }

    public void sendNotification(Long userRowId, NotificationServiceDto.NotificationInfo notification) {
        SseEmitter emitter = emitters.get(userRowId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                .name("NOTIFICATION")
                .data(notification, MediaType.APPLICATION_JSON));
            log.debug("SSE 알림 전송: userRowId={}", userRowId);
        } catch (IOException e) {
            log.error("SSE 알림 전송 실패: userRowId={}", userRowId, e);
            emitters.remove(userRowId, emitter);
        }
    }

    public void sendHeartbeat() {
        emitters.forEach((userRowId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                log.debug("SSE 하트비트 전송 실패, 연결 제거: userRowId={}", userRowId);
                emitters.remove(userRowId, emitter);
            }
        });
    }
}
