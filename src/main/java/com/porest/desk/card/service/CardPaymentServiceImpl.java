package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.core.type.YNType;
import com.porest.desk.expense.type.ExpenseType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CardPaymentServiceImpl implements CardPaymentService {

    private final CardBillingRepository cardBillingRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final EntityManager entityManager;

    @Override
    public CardPaymentServiceDto.CardBillingInfo getCardBilling(Long cardRowId, Long userRowId) {
        log.debug("카드 청구 조회: cardRowId={}, userRowId={}", cardRowId, userRowId);

        Asset card = findAssetOrThrow(cardRowId);
        validateOwnership(card, userRowId);
        validateCreditCard(card);

        LocalDate nextPaymentDate = nextPaymentDate(card.getPaymentDay(), LocalDate.now());
        BillingCycle cycle = upcomingCycle(card, nextPaymentDate);

        List<CardPaymentServiceDto.BillingInfo> history = cardBillingRepository
            .findByCardAssetRowId(cardRowId).stream()
            .map(CardPaymentServiceDto.BillingInfo::from)
            .toList();

        return new CardPaymentServiceDto.CardBillingInfo(
            cardRowId,
            cycle.amount(),
            cycle.periodStart(),
            cycle.periodEnd(),
            nextPaymentDate,
            card.getPaymentDay(),
            card.getPaymentAsset() != null ? card.getPaymentAsset().getRowId() : null,
            history
        );
    }

    @Override
    @Transactional
    public CardPaymentServiceDto.BillingInfo payCard(Long cardRowId, Long userRowId) {
        log.debug("카드 수동 결제 시작: cardRowId={}, userRowId={}", cardRowId, userRowId);

        Asset card = findAssetOrThrow(cardRowId);
        validateOwnership(card, userRowId);
        validateCreditCard(card);

        Asset paymentAsset = card.getPaymentAsset();
        if (paymentAsset == null) {
            throw new InvalidValueException(DeskErrorCode.CARD_BILLING_PAYMENT_ASSET_REQUIRED);
        }

        // 수동 결제 = 다가오는 결제 회차의 선결제 — 금액·기간 귀속 모두 그 회차 기준.
        // (종전엔 잔액 전액 + 실행일의 전월 라벨이라 회차·기간·금액이 어긋났음)
        LocalDate today = LocalDate.now();
        LocalDate nextPaymentDate = nextPaymentDate(card.getPaymentDay(), today);
        BillingCycle cycle = upcomingCycle(card, nextPaymentDate);
        long amount = cycle.amount();
        LocalDate periodStart = cycle.periodStart();
        LocalDate periodEnd = cycle.periodEnd();

        if (amount == 0L) {
            CardBilling billing = cardBillingRepository.save(
                CardBilling.skipped(card, paymentAsset, periodStart, periodEnd, today));
            log.info("카드 수동 결제 건너뜀(청구액 0): cardRowId={}", cardRowId);
            return CardPaymentServiceDto.BillingInfo.from(billing);
        }

        if (paymentAsset.getBalance() == null || paymentAsset.getBalance() < amount) {
            throw new InvalidValueException(DeskErrorCode.CARD_BILLING_INSUFFICIENT_BALANCE);
        }

        AssetServiceDto.TransferInfo transfer = createPaymentTransfer(card, paymentAsset, amount, userRowId, today);
        CardBilling billing = cardBillingRepository.save(
            CardBilling.completed(card, paymentAsset, amount, periodStart, periodEnd, today,
                transferRef(transfer.rowId())));

        log.info("카드 수동 결제 완료: cardRowId={}, transferId={}, amount={}",
            cardRowId, transfer.rowId(), amount);
        return CardPaymentServiceDto.BillingInfo.from(billing);
    }

    @Override
    @Transactional
    public void processDueCardPayments(LocalDate today) {
        log.debug("자동 카드 결제 처리 시작: today={}", today);

        List<Asset> creditCards = assetRepository.findAllByType(AssetType.CREDIT_CARD);
        int success = 0, failed = 0, skipped = 0;

        for (Asset card : creditCards) {
            // 각 건 격리 — 한 카드 실패가 전체 배치를 멈추지 않도록 try-catch
            try {
                if (!isPaymentDay(card.getPaymentDay(), today)) {
                    continue;
                }
                // 멱등성 — 이번 결제일에 이미 COMPLETED 가 있으면 skip
                if (cardBillingRepository.existsCompletedByCardAndPaymentDate(card.getRowId(), today)) {
                    log.debug("자동 카드 결제 멱등 skip: cardRowId={}, date={}", card.getRowId(), today);
                    continue;
                }

                Long userRowId = card.getUser().getRowId();
                Asset paymentAsset = card.getPaymentAsset();
                // 결제일 당일 회차 = 전월 1일~말일 사용분(선결제 차감) — 잔액 전액 아님.
                BillingCycle cycle = upcomingCycle(card, today);
                long amount = cycle.amount();
                LocalDate periodStart = cycle.periodStart();
                LocalDate periodEnd = cycle.periodEnd();

                if (amount == 0L) {
                    cardBillingRepository.save(
                        CardBilling.skipped(card, paymentAsset, periodStart, periodEnd, today));
                    skipped++;
                    continue;
                }
                if (paymentAsset == null) {
                    cardBillingRepository.save(
                        CardBilling.failed(card, null, amount, periodStart, periodEnd, today, "결제계좌 미지정"));
                    failed++;
                    continue;
                }
                if (paymentAsset.getBalance() == null || paymentAsset.getBalance() < amount) {
                    cardBillingRepository.save(
                        CardBilling.failed(card, paymentAsset, amount, periodStart, periodEnd, today, "잔액 부족"));
                    failed++;
                    continue;
                }

                AssetServiceDto.TransferInfo transfer =
                    createPaymentTransfer(card, paymentAsset, amount, userRowId, today);
                cardBillingRepository.save(
                    CardBilling.completed(card, paymentAsset, amount, periodStart, periodEnd, today,
                        transferRef(transfer.rowId())));
                success++;

                log.info("자동 카드 결제 완료: cardRowId={}, transferId={}, amount={}",
                    card.getRowId(), transfer.rowId(), amount);
            } catch (Exception e) {
                failed++;
                log.error("자동 카드 결제 실패: cardRowId={}", card.getRowId(), e);
            }
        }

        log.info("자동 카드 결제 처리 완료: 대상={}건, 성공={}, 실패={}, 건너뜀={}",
            creditCards.size(), success, failed, skipped);
    }

    /**
     * 결제 이체 생성. 회계 정합 필수: 결제는 AssetTransfer 로만 처리(Expense 생성 금지 — 지출 중복계상 방지).
     * from = 결제계좌(차감), to = 카드(잔액 0 복귀). 카드 balance 는 음수(부채)이므로 +amount 로 0 이 된다.
     */
    private AssetServiceDto.TransferInfo createPaymentTransfer(Asset card, Asset paymentAsset, long amount,
                                                              Long userRowId, LocalDate date) {
        return assetService.createTransfer(new AssetServiceDto.CreateTransferCommand(
            userRowId,
            paymentAsset.getRowId(),
            card.getRowId(),
            amount,
            0L,
            "신용카드 자동결제",
            date
        ));
    }

    /** 방금 영속화된 AssetTransfer 의 연관 참조 — 동일 트랜잭션이므로 getReference 로 충분. */
    private AssetTransfer transferRef(Long transferRowId) {
        return entityManager.getReference(AssetTransfer.class, transferRowId);
    }

    private static long absBalance(Asset card) {
        return Math.abs(card.getBalance() != null ? card.getBalance() : 0L);
    }

    /** 결제 회차 — 청구 기간(전월 1일~말일)과 그 회차의 결제 필요 잔여액. */
    record BillingCycle(LocalDate periodStart, LocalDate periodEnd, long amount) {}

    /**
     * 다가오는 결제 회차 계산. 회차 금액 = 청구 기간(결제일의 전월 1일~말일) 카드 순사용액
     * (지출 − 환불) − 같은 회차에 이미 결제 완료된 금액(선결제 차감), 최소 0.
     * 결제일 미설정(paymentDay null)이면 회차를 정의할 수 없어 종전처럼 잔액 전액을 반환한다.
     */
    private BillingCycle upcomingCycle(Asset card, LocalDate nextPaymentDate) {
        if (nextPaymentDate == null) {
            return new BillingCycle(null, null, absBalance(card));
        }
        LocalDate periodStart = periodStartFor(nextPaymentDate);
        LocalDate periodEnd = periodEndFor(nextPaymentDate);
        long spend = cycleNetSpend(card.getRowId(), periodStart, periodEnd);
        long alreadyPaid = cardBillingRepository
            .sumCompletedAmountByCardAndPeriod(card.getRowId(), periodStart, periodEnd);
        return new BillingCycle(periodStart, periodEnd, Math.max(0L, spend - alreadyPaid));
    }

    /** 청구 기간 내 카드 순사용액 — EXPENSE 합 − INCOME(환불/취소) 합. */
    private long cycleNetSpend(Long cardRowId, LocalDate start, LocalDate end) {
        Long sum = entityManager.createQuery(
            "SELECT COALESCE(SUM(CASE WHEN e.expenseType = :expenseType THEN e.amount ELSE -e.amount END), 0) " +
            "FROM Expense e " +
            "WHERE e.asset.rowId = :cardRowId " +
            "AND e.expenseDate >= :start AND e.expenseDate <= :end " +
            "AND e.isDeleted = :isDeleted", Long.class)
            .setParameter("expenseType", ExpenseType.EXPENSE)
            .setParameter("cardRowId", cardRowId)
            // expenseDate 는 LocalDateTime 이므로 LocalDate 범위를 경계 일시로 변환
            .setParameter("start", start.atStartOfDay())
            .setParameter("end", end.atTime(LocalTime.MAX))
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return sum == null ? 0L : sum;
    }

    // === 결제일 / 청구기간 계산 (RecurringTransaction MONTHLY 말일 보정 로직 재활용) ===

    /**
     * payment_day 기준 다음 결제예정일. RecurringTransactionServiceImpl.adjustToFrequency(MONTHLY) 와 동일하게
     * 해당 월 일수보다 큰 결제일은 말일로 보정한다. from(=오늘) 의 보정 결제일이 이미 지났으면 다음 달로 이월.
     */
    static LocalDate nextPaymentDate(Integer paymentDay, LocalDate from) {
        if (paymentDay == null) {
            return null;
        }
        int maxDay = from.lengthOfMonth();
        LocalDate adjusted = from.withDayOfMonth(Math.min(paymentDay, maxDay));
        if (adjusted.isBefore(from)) {
            adjusted = adjusted.plusMonths(1);
            maxDay = adjusted.lengthOfMonth();
            adjusted = adjusted.withDayOfMonth(Math.min(paymentDay, maxDay));
        }
        return adjusted;
    }

    /** today 가 이 카드의 결제일인지 — 말일 보정 포함(예: payment_day=31, 2월이면 28/29 이 결제일). */
    static boolean isPaymentDay(Integer paymentDay, LocalDate today) {
        if (paymentDay == null) {
            return false;
        }
        int maxDay = today.lengthOfMonth();
        int effectiveDay = Math.min(paymentDay, maxDay);
        return today.getDayOfMonth() == effectiveDay;
    }

    /** 청구 기간 시작 — 전월 1일. */
    static LocalDate periodStartFor(LocalDate paymentDate) {
        return paymentDate.withDayOfMonth(1).minusMonths(1);
    }

    /** 청구 기간 종료 — 전월 말일. */
    static LocalDate periodEndFor(LocalDate paymentDate) {
        return paymentDate.withDayOfMonth(1).minusDays(1);
    }

    // === 검증 ===

    private void validateCreditCard(Asset asset) {
        if (asset.getAssetType() != AssetType.CREDIT_CARD) {
            log.warn("카드 청구 대상 아님(신용카드 아님): assetId={}, type={}", asset.getRowId(), asset.getAssetType());
            throw new InvalidValueException(DeskErrorCode.CARD_BILLING_NOT_CREDIT_CARD);
        }
    }

    private void validateOwnership(Asset asset, Long userRowId) {
        if (!asset.getUser().getRowId().equals(userRowId)) {
            log.warn("카드 자산 소유권 검증 실패 - assetId={}, ownerRowId={}, requestUserRowId={}",
                asset.getRowId(), asset.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
    }

    private Asset findAssetOrThrow(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> {
                log.warn("카드 자산 조회 실패 - 존재하지 않는 자산: assetId={}", assetId);
                return new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND);
            });
    }
}
