package com.porest.desk.export.service;

import com.porest.desk.export.type.ExportPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 기간 프리셋 → 실제 [start, end] 날짜 범위 해석. front/app 과 동일 규칙.
 */
@Component
public class PeriodResolver {

    public record DateRange(LocalDate start, LocalDate end) {}

    public DateRange resolve(ExportPeriod period, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        return switch (period) {
            case THIS_MONTH -> new DateRange(thisMonth.atDay(1), thisMonth.atEndOfMonth());
            case LAST_MONTH -> {
                YearMonth last = thisMonth.minusMonths(1);
                yield new DateRange(last.atDay(1), last.atEndOfMonth());
            }
            case LAST_3_MONTHS -> new DateRange(thisMonth.minusMonths(2).atDay(1), thisMonth.atEndOfMonth());
            case THIS_YEAR -> new DateRange(LocalDate.of(today.getYear(), 1, 1), thisMonth.atEndOfMonth());
            case CUSTOM -> {
                if (customStart == null || customEnd == null) {
                    throw new IllegalArgumentException("사용자 지정 기간은 startDate/endDate 가 필요합니다.");
                }
                if (customStart.isAfter(customEnd)) {
                    throw new IllegalArgumentException("시작일이 종료일보다 늦습니다.");
                }
                yield new DateRange(customStart, customEnd);
            }
        };
    }
}
