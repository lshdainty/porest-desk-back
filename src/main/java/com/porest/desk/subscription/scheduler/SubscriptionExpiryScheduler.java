package com.porest.desk.subscription.scheduler;

import com.porest.desk.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구독 만료/갱신 일배치. 매일 자정, 만료된 구독을 auto_renew 면 기간연장, 아니면 EXPIRED 처리.
 * 결제(PG) 없음 — 갱신은 기간 연장만.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiry() {
        try {
            subscriptionService.processExpiry();
        } catch (Exception e) {
            log.error("구독 만료 배치 실패", e);
        }
    }
}
