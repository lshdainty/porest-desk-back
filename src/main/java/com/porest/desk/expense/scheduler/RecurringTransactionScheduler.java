package com.porest.desk.expense.scheduler;

import com.porest.desk.expense.service.RecurringTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionScheduler {
    private final RecurringTransactionService recurringTransactionService;

    @Scheduled(cron = "0 0 0 * * *")
    public void executeRecurringTransactions() {
        log.info("반복 거래 스케줄러 실행 시작");
        try {
            recurringTransactionService.executeDueTransactions();
            log.info("반복 거래 스케줄러 실행 완료");
        } catch (Exception e) {
            log.error("반복 거래 스케줄러 실행 중 오류 발생", e);
        }
    }
}
