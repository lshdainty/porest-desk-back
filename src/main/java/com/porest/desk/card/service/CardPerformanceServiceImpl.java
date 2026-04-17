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

    private long sumExpenseAmount(Long assetRowId, LocalDate start, LocalDate end) {
        Long sum = entityManager.createQuery(
            "SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.asset.rowId = :assetRowId " +
            "AND e.expenseType = :expenseType " +
            "AND e.expenseDate >= :start AND e.expenseDate <= :end " +
            "AND e.isDeleted = :isDeleted", Long.class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("expenseType", ExpenseType.EXPENSE)
            .setParameter("start", start)
            .setParameter("end", end)
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return sum == null ? 0L : sum;
    }
}
