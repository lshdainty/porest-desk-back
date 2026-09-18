package com.porest.desk.card.service.dto;

import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.card.type.BillingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CardPaymentServiceDto {

    /** 단일 청구 이력 행. */
    public record BillingInfo(
        Long rowId,
        Long cardAssetRowId,
        Long paymentAssetRowId,
        Long billingAmount,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        BillingStatus status,
        Long transferRowId,
        String failureReason,
        LocalDateTime createAt
    ) {
        public static BillingInfo from(CardBilling b) {
            return new BillingInfo(
                b.getRowId(),
                b.getCardAsset() != null ? b.getCardAsset().getRowId() : null,
                b.getPaymentAsset() != null ? b.getPaymentAsset().getRowId() : null,
                b.getBillingAmount(),
                b.getPeriodStart(),
                b.getPeriodEnd(),
                b.getPaymentDate(),
                b.getStatus(),
                b.getTransfer() != null ? b.getTransfer().getRowId() : null,
                b.getFailureReason(),
                b.getCreateAt()
            );
        }
    }

    /**
     * 다가오는 회차에 빠지는 할부 한 건.
     *
     * @param expenseRowId      원거래(지출) 행 아이디
     * @param merchant          가맹점 — 명세서 행의 이름
     * @param description       메모(가맹점이 없을 때의 폴백 표시용)
     * @param principalAmount   할부 원금(거래 전액)
     * @param installmentMonths 총 회차 수(N)
     * @param sequence          이번이 몇 회차인지(1-base)
     * @param amount            이번 회차에 빠지는 금액. 나머지는 1회차에 몰린다(카드사 관행)
     * @param paidOff           이 회차가 중도 전액 상환으로 남은 원금을 몰아 받은 회차인지 —
     *                          화면이 "남은 원금 정리" 표시를 달고 정리 버튼을 숨긴다
     */
    public record InstallmentDue(
        Long expenseRowId,
        String merchant,
        String description,
        Long principalAmount,
        Integer installmentMonths,
        Integer sequence,
        Long amount,
        boolean paidOff
    ) {}

    /**
     * 카드 청구 화면용 종합 응답.
     * - upcomingAmount: 다가오는 결제 회차의 결제예정액
     *   = 청구 기간(결제일의 전월 1일~말일) 순사용액 − 같은 회차 기결제액(선결제 차감).
     *   결제일 미설정 시에만 잔액 전액 fallback(기간 null).
     * - upcomingLumpSumAmount: 회차 내 일시불 순사용액(환불 상계, 음수 가능)
     * - upcomingAlreadyPaidAmount: 같은 회차 기결제액(선결제 차감분)
     * - upcomingInstallments: 이 회차에 빠지는 할부 구성 — 명세서가 원금·회차를 그린다
     * - upcomingPeriodStart/End: 다가오는 회차의 청구 기간(전월 1일~말일)
     * - nextPaymentDate: payment_day 기준 다음 결제예정일(말일 보정)
     * - paymentAssetRowId: 지정된 결제 출금계좌(없으면 null)
     * - history: 과거 청구 이력(최신순)
     */
    public record CardBillingInfo(
        Long cardAssetRowId,
        Long upcomingAmount,
        Long upcomingLumpSumAmount,
        Long upcomingAlreadyPaidAmount,
        List<InstallmentDue> upcomingInstallments,
        LocalDate upcomingPeriodStart,
        LocalDate upcomingPeriodEnd,
        LocalDate nextPaymentDate,
        Integer paymentDay,
        Long paymentAssetRowId,
        List<BillingInfo> history,
        /**
         * 다가오는 회차의 다음 회차 — 지금 쌓이고 있는 이용분(당월 1일~말일, 다음 달 결제일).
         * 없으면(결제일 미설정) null. 종전엔 다가오는 회차 하나만 내려 지금 쓰는 내역이
         * 결제일이 지나기 전엔 어느 회차에도 안 보였다.
         */
        UpcomingCycle nextCycle
    ) {}

    /** 회차 하나 — 결제일·청구 기간·예정액(선결제 차감 후)·구성. */
    public record UpcomingCycle(
        LocalDate paymentDate,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long amount,
        Long lumpSumAmount,
        Long alreadyPaidAmount,
        List<InstallmentDue> installments
    ) {}

    /**
     * 실제로 만든 환급 — 이체 아이디와 <b>금액</b>을 함께 돌려준다.
     *
     * <p>아이디는 환불 마크가 취소 때 되돌릴 이체를 가리키는 데 쓰고, 금액은 화면이
     * "결제계좌로 N원이 환급됐어요" 를 말하는 데 쓴다(설계 13-1의 사후 토스트).
     */
    public record RefundResult(Long transferRowId, long amount) {}

    /**
     * 카드 환급 미리보기 — 확인창이 "얼마가 돌아오나" 를 그릴 재료(설계 13-1).
     *
     * <p>{@code applies=false} 면 이 변경으로 돌려줄 돈이 없다. {@code reason} 은 화면이
     * 문구를 고르는 데 쓴다 — 금액 줄 · "이미 환급된 거래" 줄 · 줄 없음 세 갈래다.
     */
    public record RefundPreview(boolean applies, long refundAmount, String reason) {
        /** 카드가 아니다 — 돌려줄 자리 자체가 없다. */
        public static final String NOT_CARD = "NOT_CARD";
        /** 신용카드지만 결제계좌가 없다 — 기록용 카드다(결정 3). */
        public static final String NO_PAYMENT_ASSET = "NO_PAYMENT_ASSET";
        /** 결제 완료 회차에 낸 돈이 없거나, 아직 청구가 남아 있다. */
        public static final String NOT_PAID_CYCLE = "NOT_PAID_CYCLE";
        /** 이미 환불 마크된 거래 — 환급은 그때 끝났다. */
        public static final String ALREADY_REFUNDED = "ALREADY_REFUNDED";
        public static final String OK = "OK";

        public static RefundPreview none(String reason) {
            return new RefundPreview(false, 0L, reason);
        }

        public static RefundPreview of(long refundAmount) {
            return new RefundPreview(true, refundAmount, OK);
        }
    }

    /**
     * 아직 저장하지 않은 변경 — 미리보기가 "이 거래가 이렇게 바뀌면 회차 청구가 얼마가
     * 되나" 를 세는 데 쓴다.
     *
     * <p>실제 환급은 DB 가 이미 바뀐 뒤에 세므로 이런 게 필요 없다. 미리보기는 바뀌기
     * <b>전</b>에 세야 해서, 이 거래를 질의에서 빼고 <b>가정한 값으로 다시 더한다</b>.
     * 그래야 두 값이 같은 산식에서 나온다.
     *
     * <p>{@code amountAfter} 가 null 이면 삭제다(기여가 통째로 사라진다).
     */
    public record ExpenseChange(
        Expense expense,
        Long amountAfter,
        Long assetRowIdAfter,
        LocalDateTime dateAfter
    ) {
        public static ExpenseChange deletion(Expense expense) {
            return new ExpenseChange(expense, null, null, null);
        }

        public boolean isDeletion() {
            return amountAfter == null;
        }
    }
}
