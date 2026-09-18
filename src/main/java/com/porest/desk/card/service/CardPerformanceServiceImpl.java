package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.service.dto.CardPerformanceServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.type.ExpenseType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CardPerformanceServiceImpl implements CardPerformanceService {
    private final AssetRepository assetRepository;
    private final EntityManager entityManager;
    private final UserClock userClock;

    @Override
    public CardPerformanceServiceDto.PerformanceInfo getPerformance(CardPerformanceServiceDto.PerformanceQuery query) {
        log.debug("카드 전월 실적 조회: assetRowId={}, yearMonth={}", query.assetRowId(), query.yearMonth());

        Asset asset = assetRepository.findById(query.assetRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));

        if (!asset.getUser().getRowId().equals(query.userRowId())) {
            throw new ForbiddenException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }

        CardCatalog catalog = asset.getCardCatalog();
        if (catalog == null) {
            return CardPerformanceServiceDto.PerformanceInfo.notApplicable(query.assetRowId(), query.yearMonth());
        }

        boolean isRequired = catalog.getPerformanceIsRequired() == YNType.Y;
        int required = catalog.getPerformanceRequiredAmount() != null ? catalog.getPerformanceRequiredAmount() : 0;

        YearMonth ym = query.yearMonth();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        long current = sumExpenseAmount(query.assetRowId(), start, end,
            userClock.now(query.userRowId()));

        double rate = required > 0 ? Math.min(1.0, (double) current / required) : 1.0;
        boolean isAchieved = !isRequired || current >= required;
        long remaining = isRequired ? Math.max(0L, required - current) : 0L;

        return new CardPerformanceServiceDto.PerformanceInfo(
            query.assetRowId(),
            ym,
            required,
            catalog.getPerformanceRequiredText(),
            isRequired,
            current,
            rate,
            isAchieved,
            remaining
        );
    }

    /**
     * 실적 사용액 — 청구 예정액({@code lumpSumNet})과 <b>같은 셈법</b>이다.
     *
     * <p>한 화면에서 두 숫자가 서로 다른 규칙을 쓰면 사용자는 어느 쪽이 맞는지 알 수 없다.
     * 그래서 셋을 맞춘다.
     *
     * <ol>
     *   <li><b>환불·취소는 뺀다.</b> 종전엔 지출만 더해서, 산 것을 되돌려도 실적이 그대로
     *       남아 "이번 달 실적 달성" 이 켜졌다. 사용자는 그 표시를 믿고 어느 카드를 쓸지
     *       정한다. 실제 카드사도 취소분은 실적에서 뺀다</li>
     *   <li><b>아직 오지 않은 거래는 안 센다</b>(D1) — 반복 거래가 미리 만들어 둔 거래로
     *       실적이 먼저 달성돼 보이면 안 된다. 실적은 예측이 아니라 <b>달성도</b>다</li>
     *   <li><b>할부는 뺀다</b>(D2, 2026-09-18 결정). 종전엔 구매한 달에 전액을 실적으로
     *       쳤다 — 실제 카드사는 할부를 실적에서 빼는 쪽이 많다</li>
     * </ol>
     */
    private long sumExpenseAmount(Long assetRowId, LocalDate start, LocalDate end,
                                  LocalDateTime now) {
        // expenseDate 는 LocalDateTime 이므로 LocalDate 범위를 경계 일시로 변환하고,
        // 상한은 지금까지로 조인다.
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime periodEnd = end.atTime(LocalTime.MAX);
        LocalDateTime to = now.isBefore(periodEnd) ? now : periodEnd;
        if (!from.isBefore(to)) {
            return 0L;
        }
        Long sum = entityManager.createQuery(
            "SELECT COALESCE(SUM(CASE WHEN e.expenseType = :expenseType " +
            "THEN e.amount ELSE -e.amount END), 0) FROM Expense e " +
            "WHERE e.asset.rowId = :assetRowId " +
            "AND e.expenseDate >= :start AND e.expenseDate <= :end " +
            "AND (e.installmentMonths IS NULL OR e.installmentMonths <= 1) " +
            "AND e.isDeleted = :isDeleted", Long.class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("expenseType", ExpenseType.EXPENSE)
            .setParameter("start", from)
            .setParameter("end", to)
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return sum == null ? 0L : sum;
    }
}
