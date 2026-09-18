package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
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
import java.time.LocalTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CardPerformanceServiceImpl implements CardPerformanceService {
    private final AssetRepository assetRepository;
    private final EntityManager entityManager;

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

        long current = sumExpenseAmount(query.assetRowId(), start, end);

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
     * 실적 사용액 — <b>환불·취소는 뺀다.</b>
     *
     * <p>종전엔 지출만 더하고 환불을 안 뺐다. 그래서 산 것을 되돌려도 실적은 그대로 남아
     * "이번 달 실적 달성" 이 켜졌고, 사용자는 그 표시를 믿고 혜택이 붙는다고 여긴다 —
     * 되돌릴 수 없는 판단(어느 카드를 쓸까)에 쓰이는 숫자라 더 그렇다. 실제 카드사도
     * 취소분은 실적에서 뺀다.
     *
     * <p>같은 화면의 청구 예정액({@code lumpSumNet})과 셈법을 맞춘다 — 한 화면에서 두
     * 숫자가 서로 다른 규칙을 쓰면 사용자는 어느 쪽이 맞는지 알 수 없다.
     */
    private long sumExpenseAmount(Long assetRowId, LocalDate start, LocalDate end) {
        Long sum = entityManager.createQuery(
            "SELECT COALESCE(SUM(CASE WHEN e.expenseType = :expenseType " +
            "THEN e.amount ELSE -e.amount END), 0) FROM Expense e " +
            "WHERE e.asset.rowId = :assetRowId " +
            "AND e.expenseDate >= :start AND e.expenseDate <= :end " +
            "AND e.isDeleted = :isDeleted", Long.class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("expenseType", ExpenseType.EXPENSE)
            // expenseDate 는 LocalDateTime 이므로 LocalDate 범위를 경계 일시로 변환
            .setParameter("start", start.atStartOfDay())
            .setParameter("end", end.atTime(LocalTime.MAX))
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return sum == null ? 0L : sum;
    }
}
