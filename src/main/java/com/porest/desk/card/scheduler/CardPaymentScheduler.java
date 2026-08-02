package com.porest.desk.card.scheduler;

import com.porest.desk.card.service.CardPaymentService;
import com.porest.desk.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardPaymentScheduler {
    private final CardPaymentService cardPaymentService;
    private final ServiceClock serviceClock;

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void processDueCardPayments() {
        log.info("신용카드 자동결제 스케줄러 실행 시작");
        try {
            cardPaymentService.processDueCardPayments(serviceClock.today());
            log.info("신용카드 자동결제 스케줄러 실행 완료");
        } catch (Exception e) {
            log.error("신용카드 자동결제 스케줄러 실행 중 오류 발생", e);
        }
    }
}
