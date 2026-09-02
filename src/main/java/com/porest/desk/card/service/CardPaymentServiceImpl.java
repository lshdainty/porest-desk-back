package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;
import jakarta.persistence.EntityManager;
import com.porest.core.time.UserClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.porest.desk.card.type.BillingStatus;
import java.util.Objects;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CardPaymentServiceImpl implements CardPaymentService {

    private final CardBillingRepository cardBillingRepository;
    private final UserClock userClock;
    private final AssetRepository assetRepository;
    private final AssetService assetService;
    /** 결제계좌 없이 카드만 정리할 때 쓴다 — 이체를 못 만드니 카드에 직접 상계 flow 를 쌓는다. */
    private final AssetBalanceHistoryService balanceHistoryService;
    private final EntityManager entityManager;

    @Override
    public CardPaymentServiceDto.CardBillingInfo getCardBilling(Long cardRowId, Long userRowId) {
        log.debug("카드 청구 조회: cardRowId={}, userRowId={}", cardRowId, userRowId);

        Asset card = findAssetOrThrow(cardRowId);
        validateOwnership(card, userRowId);
        validateCreditCard(card);

        LocalDate nextPaymentDate = nextPaymentDate(card.getPaymentDay(), userClock.today(userRowId));
        BillingCycle cycle = upcomingCycle(card, nextPaymentDate);

        List<CardPaymentServiceDto.BillingInfo> history = cardBillingRepository
            .findByCardAssetRowId(cardRowId).stream()
            .map(CardPaymentServiceDto.BillingInfo::from)
            .toList();

        // 다음 회차 — 지금 쌓이는 이용분. 결제일이 지나기 전에도 이번 달에 얼마 썼는지,
        // 미리 얼마를 낼 수 있는지 보여 주려고 하나 더 내려준다.
        CardPaymentServiceDto.UpcomingCycle nextCycle = null;
        if (nextPaymentDate != null) {
            LocalDate following = nextPaymentDate(card.getPaymentDay(), nextPaymentDate.plusDays(1));
            BillingCycle c2 = upcomingCycle(card, following);
            nextCycle = new CardPaymentServiceDto.UpcomingCycle(following, c2.periodStart(), c2.periodEnd(),
                c2.amount(), c2.lumpSumAmount(), c2.alreadyPaid(), c2.installments());
        }

        return new CardPaymentServiceDto.CardBillingInfo(
            cardRowId,
            cycle.amount(),
            cycle.lumpSumAmount(),
            cycle.alreadyPaid(),
            cycle.installments(),
            cycle.periodStart(),
            cycle.periodEnd(),
            nextPaymentDate,
            card.getPaymentDay(),
            card.getPaymentAsset() != null ? card.getPaymentAsset().getRowId() : null,
            history,
            nextCycle
        );
    }

    @Override
    @Transactional
    public CardPaymentServiceDto.BillingInfo payCard(Long cardRowId, Long userRowId, Long amount) {
        return payCard(cardRowId, userRowId, amount, null);
    }

    @Override
    @Transactional
    public CardPaymentServiceDto.BillingInfo payCard(Long cardRowId, Long userRowId, Long amount,
                                                     LocalDate paymentDate) {
        log.debug("카드 수동 결제 시작: cardRowId={}, userRowId={}, amount={}, paymentDate={}",
            cardRowId, userRowId, amount, paymentDate);

        Asset card = findAssetOrThrow(cardRowId);
        validateOwnership(card, userRowId);
        validateCreditCard(card);

        // 결제계좌는 필수가 아니다 — 이건 기록용 앱이라 통장을 안 적고 가계부+카드만 쓰는
        // 사용자가 있다. 계좌가 없으면 이체 없이 카드 사용액만 정리한다(규칙6 과 같은 논리:
        // 등록 안 한 자산은 애초에 순자산에 안 잡혀 있으므로 카드 쪽만 맞추면 된다).
        Asset paymentAsset = card.getPaymentAsset();

        // 수동 결제 = 선택한 회차의 선결제 — 금액·기간 귀속 모두 그 회차 기준.
        // (종전엔 잔액 전액 + 실행일의 전월 라벨이라 회차·기간·금액이 어긋났음)
        // paymentDate 가 없으면 다가오는 회차, 있으면 다가오는 회차 또는 그 다음 회차(지금 쌓이는
        // 이용분)만 허용한다 — 그 밖의 날짜는 화면이 내려준 회차가 아니므로 거절한다.
        LocalDate today = userClock.today(userRowId);
        LocalDate nextPaymentDate = nextPaymentDate(card.getPaymentDay(), today);
        LocalDate target = nextPaymentDate;
        if (paymentDate != null && nextPaymentDate != null && !paymentDate.equals(nextPaymentDate)) {
            LocalDate following = nextPaymentDate(card.getPaymentDay(), nextPaymentDate.plusDays(1));
            if (!paymentDate.equals(following)) {
                throw new InvalidValueException(DeskErrorCode.CARD_BILLING_INVALID_CYCLE);
            }
            target = following;
        }
        BillingCycle cycle = upcomingCycle(card, target);
        long remaining = cycle.amount();
        LocalDate periodStart = cycle.periodStart();
        LocalDate periodEnd = cycle.periodEnd();

        // 부분 선결제 — 남은 청구액 안에서만. 청구액은 이미 '사용액 − 이미 결제한 금액' 이라
        // 나머지는 다음 결제일에 정상적으로 빠진다.
        long payAmount = amount != null ? amount : remaining;
        if (amount != null && (amount <= 0L || amount > remaining)) {
            throw new InvalidValueException(DeskErrorCode.CARD_BILLING_INVALID_AMOUNT);
        }

        if (payAmount == 0L) {
            CardBilling billing = cardBillingRepository.save(
                CardBilling.skipped(card, paymentAsset, periodStart, periodEnd, today));
            log.info("카드 수동 결제 건너뜀(청구액 0): cardRowId={}", cardRowId);
            return CardPaymentServiceDto.BillingInfo.from(billing);
        }

        // 잔액 부족을 막지 않는다 — 기록용 앱이라 통장 잔액을 안 맞춰 둔 사용자가 많고,
        // 실제로는 결제됐는데 앱에서만 결제가 안 되는 상태를 만들 이유가 없다(마이너스 통장도 있다).
        // 이체 시각은 누른 시각이다 — 결제일 00:00 으로 찍으면 같은 날 그 뒤에 만든 통장 INIT 이나
        // 카드 잔액 수정(MANUAL) 앵커보다 과거가 되어 양쪽 잔액 집계에서 사라진다(이체 행은 있는데
        // 통장·카드가 그대로). 자동 결제는 결제일 자정에 도는 배치라 그날 시작이 곧 실행 시각이다.
        CardBilling billing = cardBillingRepository.save(payoff(
            card, paymentAsset, payAmount, periodStart, periodEnd, today, userRowId,
            userClock.now(userRowId)));

        log.info("카드 수동 결제 완료: cardRowId={}, amount={}, 남은청구액={}",
            cardRowId, payAmount, remaining - payAmount);
        return CardPaymentServiceDto.BillingInfo.from(billing);
    }

    @Override
    @Transactional
    public void cancelPayment(Long billingRowId, Long userRowId) {
        log.debug("카드 결제 취소 시작: billingRowId={}", billingRowId);

        CardBilling billing = cardBillingRepository.findById(billingRowId)
            .filter(b -> b.getIsDeleted() == YNType.N)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.CARD_BILLING_NOT_FOUND));
        if (!Objects.equals(billing.getCardAsset().getUser().getRowId(), userRowId)) {
            throw new InvalidValueException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
        // 이미 무른 회차(취소·건너뜀)는 되돌릴 게 없다.
        if (billing.getStatus() != BillingStatus.COMPLETED) {
            throw new InvalidValueException(DeskErrorCode.CARD_BILLING_NOT_CANCELABLE);
        }

        // 결제로 만든 이체를 무른다. 그 안에서 잔액 이력이 되돌아가고 청구 회차도 cancel 된다
        // (deleteTransfer 가 findActiveByTransfer 로 찾아 취소한다) — 여기서 또 부르지 않는다.
        if (billing.getTransfer() != null) {
            assetService.deleteTransfer(billing.getTransfer().getRowId(), userRowId);
        } else {
            // 이체 없이 기록만 남은 회차 — 청구만 되돌린다.
            billing.cancel();
        }

        log.info("카드 결제 취소 완료: billingRowId={}, cardRowId={}",
            billingRowId, billing.getCardAsset().getRowId());
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
                if (isPaymentDay(card.getPaymentDay(), today)) {
                    switch (payDueCard(card, today)) {
                        case PAID -> success++;
                        case SKIPPED -> skipped++;
                        case ALREADY_DONE -> { }
                    }
                }
                // 환급은 결제일과 무관하게 매일 본다 — 환불은 아무 날에나 들어오고,
                // 결제일까지 기다리면 그동안 잔액이 양수로 떠 있게 된다.
                refundOverpaymentIfAny(card, today);
            } catch (Exception e) {
                failed++;
                log.error("자동 카드 결제 실패: cardRowId={}", card.getRowId(), e);
            }
        }

        log.info("자동 카드 결제 처리 완료: 대상={}건, 성공={}, 실패={}, 건너뜀={}",
            creditCards.size(), success, failed, skipped);
    }

    /** 결제일 처리 결과 — 집계용. */
    private enum PayOutcome { PAID, SKIPPED, ALREADY_DONE }

    /** 결제일 당일 청구 결제. 루프에서 환급 스윕과 나란히 두려고 분리했다. */
    private PayOutcome payDueCard(Asset card, LocalDate today) {
        // 멱등성 — 이번 결제일에 이미 COMPLETED 가 있으면 skip
        if (cardBillingRepository.existsCompletedByCardAndPaymentDate(card.getRowId(), today)) {
            log.debug("자동 카드 결제 멱등 skip: cardRowId={}, date={}", card.getRowId(), today);
            return PayOutcome.ALREADY_DONE;
        }

        Long userRowId = card.getUser().getRowId();
        Asset paymentAsset = card.getPaymentAsset();
        // 결제일 당일 회차 = 전월 1일~말일 사용분(선결제 차감) — 잔액 전액 아님.
        BillingCycle cycle = upcomingCycle(card, today);
        long amount = cycle.amount();

        if (amount == 0L) {
            cardBillingRepository.save(
                CardBilling.skipped(card, paymentAsset, cycle.periodStart(), cycle.periodEnd(), today));
            return PayOutcome.SKIPPED;
        }
        // 결제계좌 미지정·잔액 부족으로 실패시키지 않는다 — 기록용 앱이라 통장을 안 적거나
        // 잔액을 안 맞춰 둔 사용자가 있고, 막으면 카드 부채가 영원히 안 지워진다.
        cardBillingRepository.save(payoff(
            card, paymentAsset, amount, cycle.periodStart(), cycle.periodEnd(), today, userRowId,
            today.atStartOfDay()));

        log.info("자동 카드 결제 완료: cardRowId={}, amount={}, 결제계좌={}",
            card.getRowId(), amount, paymentAsset != null ? paymentAsset.getRowId() : "미지정");
        return PayOutcome.PAID;
    }

    /**
     * 카드 잔액이 양수(과납·환불 초과분)면 실제 카드사처럼 결제계좌로 돌려준다.
     *
     * <p>결제를 다 낸 뒤 환불이 들어오면 카드 잔액이 양수가 된다. 실제 카드사는 그 돈을
     * 결제계좌로 환급하거나 다음 청구에서 뺀다 — 카드에 잔고가 쌓여 있는 상태로 두지 않는다.
     *
     * <p>멱등은 따로 기록할 필요가 없다. 환급하면 잔액이 0 이 되므로 다음 실행에서 그냥 걸러진다.
     */
    private void refundOverpaymentIfAny(Asset card, LocalDate today) {
        Long userRowId = card.getUser().getRowId();
        long surplus = balanceHistoryService.balanceAt(card, userClock.now(userRowId)).total();
        if (surplus <= 0L) {
            return;
        }

        Asset paymentAsset = card.getPaymentAsset();
        if (paymentAsset != null) {
            // 카드 → 결제계좌. 결제 이체(createPaymentTransfer)의 거울상이다.
            assetService.createTransfer(new AssetServiceDto.CreateTransferCommand(
                userRowId,
                card.getRowId(),
                paymentAsset.getRowId(),
                surplus,
                0L,
                0L,
                "신용카드 환급",
                today.atStartOfDay(),
                // 환급도 잔액에 묶여 있다 — 이 이체만 따로 고치면 카드 잔액이 다시 어긋난다.
                "CARD_REFUND"
            ));
        } else {
            // 결제계좌를 안 적은 사용자 — 이체를 만들 곳이 없으니 카드 쪽만 0 으로 맞춘다.
            // payoff 의 상계 fallback 과 같은 논리이고 방향만 반대다(+상계 ↔ −상계).
            balanceHistoryService.recordExpense(
                card, null, ExpenseType.EXPENSE, surplus, today.atStartOfDay());
        }

        log.info("카드 과납 환급: cardRowId={}, surplus={}, 결제계좌={}",
            card.getRowId(), surplus, paymentAsset != null ? paymentAsset.getRowId() : "미지정(상계)");
    }

    /**
     * 결제 이체 생성. 회계 정합 필수: 결제는 AssetTransfer 로만 처리(Expense 생성 금지 — 지출 중복계상 방지).
     * from = 결제계좌(차감), to = 카드(잔액 0 복귀). 카드 balance 는 음수(부채)이므로 +amount 로 0 이 된다.
     */
    /**
     * 카드 사용액 정산 — 결제계좌가 있으면 이체로, 없으면 카드에만 상계 flow 로 처리한다.
     *
     * <p>결제계좌를 안 적는 사용자가 있다(가계부+카드만 쓰는 경우). 그때 이체를 못 만든다고
     * 결제를 막으면 카드 사용액이 영원히 안 지워지고 부채로 계속 쌓인다. 등록 안 한 통장은
     * 애초에 순자산에 안 잡혀 있으므로 카드 쪽만 0 으로 맞추면 일관된다(규칙6 과 같은 논리).
     */
    private CardBilling payoff(Asset card, Asset paymentAsset, long amount, LocalDate periodStart,
                               LocalDate periodEnd, LocalDate today, Long userRowId,
                               LocalDateTime transferAt) {
        // 돈은 남은 빚까지만 움직인다 — 청구(기록)는 회차 사용액 전액, 이체는 min(청구, 빚).
        // 잔액을 수동 보정(앵커)해 빚을 지워 둔 카드는 그 지출이 이미 잔액에 정리된 상태라,
        // 전액을 또 이체하면 카드가 양수로 뒤집히고 다음 날 과납 환급 스윕이 도로 돌려보낸다
        // — 아무 일도 아닌 왕복이 기록만 어지럽힌다. 그래서 역전 방지는 표시(upcomingCycle)가
        // 아니라 여기서 건다.
        long move = Math.min(amount, currentDebt(card));
        if (paymentAsset != null && move > 0L) {
            AssetServiceDto.TransferInfo transfer =
                createPaymentTransfer(card, paymentAsset, move, userRowId, transferAt);
            return CardBilling.completed(card, paymentAsset, amount, periodStart, periodEnd, today,
                transferRef(transfer.rowId()));
        }
        if (paymentAsset == null && move > 0L) {
            // 이체 없이 카드 잔액만 되돌린다 — 사용액이 음수로 쌓여 있으므로 +금액 flow.
            balanceHistoryService.recordExpense(
                card, null, ExpenseType.INCOME, move, transferAt);
        }
        // move == 0(빚 없음)이면 돈·잔액을 건드리지 않고 회차 완료만 기록한다.
        return CardBilling.completed(card, paymentAsset, amount, periodStart, periodEnd, today, null);
    }

    private AssetServiceDto.TransferInfo createPaymentTransfer(Asset card, Asset paymentAsset, long amount,
                                                              Long userRowId, LocalDateTime at) {
        return assetService.createTransfer(new AssetServiceDto.CreateTransferCommand(
            userRowId,
            paymentAsset.getRowId(),
            card.getRowId(),
            amount,
            0L,
            0L, // 카드 결제는 이자 개념이 없다 — 할부 이자는 청구액에 이미 포함돼 들어온다
            "신용카드 자동결제",
            // 수동 결제는 누른 시각, 자동 결제는 결제일 자정(배치 실행 시각) — 호출자가 정한다.
            at,
            // 카드 결제는 청구 회차와 묶여 있다 — 이 이체만 따로 고치면 청구가 어긋난다.
            "CARD_PAYMENT"
        ));
    }

    /** 방금 영속화된 AssetTransfer 의 연관 참조 — 동일 트랜잭션이므로 getReference 로 충분. */
    private AssetTransfer transferRef(Long transferRowId) {
        return entityManager.getReference(AssetTransfer.class, transferRowId);
    }

    /** 지금 이 카드에 남은 빚(양수). 잔액이 0 이상이면 빚이 없다는 뜻이라 0. */
    private long currentDebt(Asset card) {
        // 캐시 컬럼이 아니라 이력 집계 — 청구액을 낡은 값으로 잡으면 결제 금액이 틀어진다.
        return Math.max(0L, -balanceHistoryService
            .balanceAt(card, userClock.now(card.getUser().getRowId())).total());
    }

    /** 결제 회차 — 청구 기간(전월 1일~말일)과 그 회차의 결제 필요 잔여액. */
    /**
     * @param amount        결제예정액 = max(0, 일시불 + 할부 회차 합 − 기결제)
     * @param lumpSumAmount 일시불 순사용액(EXPENSE − 환불). 환불이 크면 음수일 수 있다
     * @param alreadyPaid   같은 회차에 이미 낸 금액(선결제 차감분)
     * @param installments  이 회차에 빠지는 할부 회차들 — 명세서가 원금·회차를 그릴 재료
     */
    record BillingCycle(LocalDate periodStart, LocalDate periodEnd, long amount,
                        long lumpSumAmount, long alreadyPaid,
                        List<CardPaymentServiceDto.InstallmentDue> installments) {}

    /**
     * 다가오는 결제 회차 계산. 회차 금액 = 청구 기간(결제일의 전월 1일~말일) 카드 순사용액
     * (지출 − 환불) − 같은 회차에 이미 결제 완료된 금액(선결제 차감), 최소 0.
     *
     * <p><b>현재 카드빚으로 캡하지 않는다.</b> 잔액을 수동 보정(앵커)해 0 으로 맞춘 카드는
     * 빚이 0 으로 계산되는데, 종전엔 그 캡이 여기(표시)에 걸려 있어 사용액이 있어도
     * 결제예정액이 0 이 되고 결제 버튼이 죽었다(문자 수입으로 지출만 기록하는 사용자의
     * 실제 사고). 양수 역전 방지는 표시가 아니라 결제 시점이 맡는다 — {@link #payoff} 가
     * 돈을 남은 빚까지만 움직인다.
     */
    /**
     * 할부 중도 전액 상환.
     *
     * <p>상환일을 "오늘" 이 아니라 <b>다가오는 청구 회차의 시작일</b>로 적는 게 핵심이다.
     * 오늘로 적으면(예: 9/1, 결제일 6일) 남은 원금이 9월 회차 — 10월 6일 청구 — 로 밀려
     * "지금 정리하고 싶다" 는 의도와 한 달 어긋난다. 다가오는 회차(8월분, 9/6 청구)에
     * 몰아야 화면의 예정액이 즉시 커지고 지금 결제로 바로 정리된다.
     */
    @Override
    @Transactional
    public void payoffInstallment(Long cardRowId, Long expenseRowId, Long userRowId) {
        PayoffTarget t = payoffTarget(cardRowId, expenseRowId, userRowId);
        if (t.expense().getInstallmentPayoffDate() != null) {
            throw new InvalidValueException(DeskErrorCode.CARD_INSTALLMENT_ALREADY_PAID_OFF);
        }
        // 다가오는 회차 기준으로도 이미 끝난 할부면 정리할 남은 원금이 없다.
        if (Math.max(1, t.expense().installmentSequenceAt(t.anchor()))
                > t.expense().getInstallmentMonths()) {
            throw new InvalidValueException(DeskErrorCode.CARD_INSTALLMENT_FINISHED);
        }
        t.expense().payoffInstallment(t.anchor());
        log.info("할부 중도 전액 상환: expenseRowId={}, anchor={}", expenseRowId, t.anchor());
    }

    /** 상환 취소 — 정상 분할로 되돌린다. 잘못 누른 상환을 무르는 경로. */
    @Override
    @Transactional
    public void cancelInstallmentPayoff(Long cardRowId, Long expenseRowId, Long userRowId) {
        PayoffTarget t = payoffTarget(cardRowId, expenseRowId, userRowId);
        if (t.expense().getInstallmentPayoffDate() == null) {
            throw new InvalidValueException(DeskErrorCode.CARD_INSTALLMENT_NOT_PAID_OFF);
        }
        t.expense().cancelInstallmentPayoff();
        log.info("할부 상환 취소: expenseRowId={}", expenseRowId);
    }

    private record PayoffTarget(Expense expense, LocalDate anchor) {}

    /** 상환 대상 검증 — 카드 소유·신용카드·그 카드의 살아 있는 할부 거래. */
    private PayoffTarget payoffTarget(Long cardRowId, Long expenseRowId, Long userRowId) {
        Asset card = findAssetOrThrow(cardRowId);
        validateOwnership(card, userRowId);
        validateCreditCard(card);

        Expense expense = entityManager.find(Expense.class, expenseRowId);
        if (expense == null || expense.getIsDeleted() == YNType.Y
                || expense.getAsset() == null
                || !Objects.equals(expense.getAsset().getRowId(), cardRowId)) {
            // 없는 것과 남의 것을 구분해 주지 않는다 — 존재 자체가 정보다.
            throw new EntityNotFoundException(DeskErrorCode.CARD_INSTALLMENT_NOT_FOUND);
        }
        if (!expense.isInstallment()) {
            throw new InvalidValueException(DeskErrorCode.CARD_INSTALLMENT_NOT_INSTALLMENT);
        }

        LocalDate today = userClock.today(userRowId);
        LocalDate next = nextPaymentDate(card.getPaymentDay(), today);
        // upcomingCycle 과 같은 규칙 — 결제일이 없으면 당월 1일~말일 회차.
        LocalDate anchor = next == null ? today.withDayOfMonth(1) : periodStartFor(next);
        return new PayoffTarget(expense, anchor);
    }

    private BillingCycle upcomingCycle(Asset card, LocalDate nextPaymentDate) {
        LocalDate periodStart;
        LocalDate periodEnd;
        if (nextPaymentDate == null) {
            // 결제일 미설정 — 회차를 당월 1일~말일로 본다(체크카드 월 사용액과 같은 기준).
            // 종전엔 잔액 전액을 청구했는데, 절대값이라 잔액이 양수여도 그만큼 또 청구해
            // 결제할수록 더 양수가 되는 결함이 있었다.
            LocalDate today = userClock.today(card.getUser().getRowId());
            periodStart = today.withDayOfMonth(1);
            periodEnd = today.withDayOfMonth(today.lengthOfMonth());
        } else {
            periodStart = periodStartFor(nextPaymentDate);
            periodEnd = periodEndFor(nextPaymentDate);
        }
        long lumpSum = lumpSumNet(card.getRowId(), periodStart, periodEnd);
        List<CardPaymentServiceDto.InstallmentDue> installments =
            installmentDuesIn(card.getRowId(), periodStart, periodEnd);
        long installmentSum = installments.stream()
            .mapToLong(CardPaymentServiceDto.InstallmentDue::amount).sum();
        long alreadyPaid = cardBillingRepository
            .sumCompletedAmountByCardAndPeriod(card.getRowId(), periodStart, periodEnd);
        return new BillingCycle(periodStart, periodEnd,
            Math.max(0L, lumpSum + installmentSum - alreadyPaid),
            lumpSum, alreadyPaid, installments);
    }

    /** 일시불 순사용액 — EXPENSE 합 − INCOME(환불/취소) 합. 할부 거래는 제외한다. */
    private long lumpSumNet(Long cardRowId, LocalDate start, LocalDate end) {
        Long sum = entityManager.createQuery(
            "SELECT COALESCE(SUM(CASE WHEN e.expenseType = :expenseType THEN e.amount ELSE -e.amount END), 0) " +
            "FROM Expense e " +
            "WHERE e.asset.rowId = :cardRowId " +
            "AND e.expenseDate >= :start AND e.expenseDate <= :end " +
            "AND (e.installmentMonths IS NULL OR e.installmentMonths <= 1) " +
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

    /**
     * 이 청구 기간에 빠질 할부 회차 합.
     *
     * <p>거래가 속한 청구 기간을 1회차로 보고, 이번 기간이 몇 회차인지를 <b>기간 시작월의 차이</b>로
     * 센다(결제일 말일 보정과 무관하게 월 단위로 세어야 회차가 밀리지 않는다).
     * 회차가 1..N 범위 밖이면 이 기간엔 청구되지 않는다.
     */
    /**
     * 이 청구 기간에 빠질 할부 회차 목록 — 합계가 아니라 <b>구성</b>을 돌려준다.
     *
     * <p>명세서에 "원금 얼마짜리 할부의 몇 번째 회차가 얼마 빠진다" 를 그리려면 합계로는
     * 부족하다. 합만 내리던 시절에는 할부 거래가 있는 달의 예정액이 이용 내역 합과 달라
     * "이 숫자가 어디서 왔는지" 를 화면이 설명할 수 없었다.
     */
    private List<CardPaymentServiceDto.InstallmentDue> installmentDuesIn(
            Long cardRowId, LocalDate start, LocalDate end) {
        // 아직 회차가 남아 있을 수 있는 할부 거래만 — 이 기간보다 미래 거래는 볼 필요가 없다.
        List<Expense> installments = entityManager.createQuery(
            "SELECT e FROM Expense e " +
            "WHERE e.asset.rowId = :cardRowId " +
            "AND e.expenseType = :expenseType " +
            "AND e.installmentMonths > 1 " +
            "AND e.expenseDate <= :end " +
            "AND e.isDeleted = :isDeleted " +
            "ORDER BY e.expenseDate", Expense.class)
            .setParameter("cardRowId", cardRowId)
            .setParameter("expenseType", ExpenseType.EXPENSE)
            .setParameter("end", end.atTime(LocalTime.MAX))
            .setParameter("isDeleted", YNType.N)
            .getResultList();

        List<CardPaymentServiceDto.InstallmentDue> dues = new ArrayList<>();
        for (Expense e : installments) {
            int seq = e.installmentSequenceAt(start);
            long due = e.installmentAmountAt(seq);
            if (due == 0L) {
                // 회차 범위(1..N) 밖 — 이 기간엔 청구되지 않는 할부다.
                continue;
            }
            Integer payoffSeq = e.installmentPayoffSequence();
            dues.add(new CardPaymentServiceDto.InstallmentDue(
                e.getRowId(), e.getMerchant(), e.getDescription(),
                e.getAmount(), e.getInstallmentMonths(), seq, due,
                payoffSeq != null && payoffSeq == seq));
        }
        return dues;
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
