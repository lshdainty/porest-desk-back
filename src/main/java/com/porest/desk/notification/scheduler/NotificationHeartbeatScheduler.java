package com.porest.desk.notification.scheduler;

import com.porest.desk.notification.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationHeartbeatScheduler {
    private final SseEmitterService sseEmitterService;

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        sseEmitterService.sendHeartbeat();
    }
}
