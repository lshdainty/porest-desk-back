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
     * @param recordOnly        기록용 회차분 — 결제가 끝난 회차에 뒤늦게 적어 계좌에서 안 빠졌다("· 기록만")
     */
    public record InstallmentDue(
        Long expenseRowId,
        String merchant,
        String description,
        Long principalAmount,
        Integer installmentMonths,
        Integer sequence,
        Long amount,
        boolean paidOff,
        boolean recordOnly
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
        UpcomingCycle nextCycle,
        /**
         * 닫힌 회차(결제일이 지난) — 회차 선택기의 과거 칸. 결제 기록이 있는 회차와 기록용
         * 거래가 있는 회차의 합집합이다. 종전엔 결제 기록이 있는 회차만 골라져서, 소급 입력한
         * 거래가 떨어진 회차는 볼 방법이 없었다(닫힌 회차 규칙 R2·R4).
         */
        List<ClosedCycle> closedCycles
    ) {}

    /**
     * 닫힌 회차 하나 — 명세서 머리 금액은 {@code recordedAmount}(지금 기록 합, D10).
     *
     * @param paymentDate         그 회차에 실제로 적용된 결제일(D5)
     * @param paidAmount          앱이 실제로 결제한 순 금액(결제 − 환급)
     * @param recordedAmount      그 회차의 지금 기록 합 — 일시불 지출 − 카드 수입 + 할부 회차분(환불 제외, 기록용 포함)
     * @param recordedOnlyAmount  하위 호환 — {@code max(0, recorded − paid)}
     * @param preRegistration     카드 등록 전 회차 — 실제와 안 맞을 수 있다는 주의 문구용(R4)
     * @param refundableUntil     폐지(D1) — 늘 null. 옛 클라이언트가 필드를 기대해 남긴다
     * @param installmentDues     그 회차의 할부 회차분 — 거래 날짜가 앞 달이라 이용 내역 목록에 안 나오는 몫(24차 8)
     */
    public record ClosedCycle(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        long paidAmount,
        long recordedAmount,
        long recordedOnlyAmount,
        boolean preRegistration,
        LocalDate refundableUntil,
        List<InstallmentDue> installmentDues
    ) {}

    /**
     * 거래 한 건의 변경을 카드 회차 규칙으로 정산해 달라는 요청.
     *
     * @param expense              대상 거래 — 실행은 바뀐 뒤 상태, 미리보기는 바뀌기 전 상태
     * @param before               바뀌기 전 모습(create 는 none)
     * @param after                바뀐 뒤 모습(delete·환불은 none)
     * @param refundMemo           환급 이체 메모
     * @param mode                 무슨 변경인가 — 환불과 환불 취소는 서로를 정확히 되돌려야 해서 따로 다룬다
     */
    public record SettlementCommand(
        Expense expense,
        com.porest.desk.card.service.CardCycleMath.Side before,
        com.porest.desk.card.service.CardCycleMath.Side after,
        String refundMemo,
        Long userRowId,
        SettlementMode mode
    ) {}

    /**
     * 정산 종류.
     *
     * <ul>
     *   <li>{@code CHANGE} — 저장·수정·삭제. 표식을 새로 세고 상계를 더하고 뺀다</li>
     *   <li>{@code REFUND} — 환불 마크. 표식과 상계는 그대로 두고, 이번에 붙잡은 몫은 따로
     *       ({@code CARD_REFUND_HOLD}) 남긴다 — 환불 취소가 그것만 지우면 환불 전 모습으로 돌아간다</li>
     *   <li>{@code CANCEL_REFUND} — 환불 취소. 환불 동안 결제일이 지나 그 결제에 이 거래가 빠진
     *       회차는 기록용으로, 오늘이 결제일인 회차는 그 자리 결제로 채운다</li>
     * </ul>
     */
    public enum SettlementMode { CHANGE, REFUND, CANCEL_REFUND }

    /** 정산 결과 — 선결제 환급 이체·금액, 새 표식, 새로 기록용이 된 금액. */
    public record SettlementResult(
        Long refundTransferRowId,
        long refundedAmount,
        LocalDate newMark,
        long newRecordAmount
    ) {}

    /**
     * 정산 미리보기 — 옛 앱의 확인창 재료(새 웹·앱은 부르지 않는다, D4).
     *
     * @param refundAmount    결제계좌로 돌아갈 금액(열린 회차 선결제가 남을 때만)
     * @param noPaymentAsset  결제계좌가 없는 카드
     * @param newRecordAmount 새로 기록용이 되는 금액(닫힌 회차 저장)
     */
    public record SettlementPreview(
        long refundAmount,
        boolean noPaymentAsset,
        long newRecordAmount
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
     * 미리보기 사유 — 화면이 확인창 문구를 고르는 키(설계 13-1, 닫힌 회차 R6).
     */
    public static final class RefundPreview {
        private RefundPreview() {}

        /** 카드가 아니다 — 돌려줄 자리 자체가 없다. */
        public static final String NOT_CARD = "NOT_CARD";
        /** 신용카드지만 결제계좌가 없다 — 기록용 카드다(결정 3). */
        public static final String NO_PAYMENT_ASSET = "NO_PAYMENT_ASSET";
        /** 결제 완료 회차에 낸 돈이 없거나, 아직 청구가 남아 있다. */
        public static final String NOT_PAID_CYCLE = "NOT_PAID_CYCLE";
        /** 이미 환불 마크된 거래 — 환급은 그때 끝났다. */
        public static final String ALREADY_REFUNDED = "ALREADY_REFUNDED";
        public static final String OK = "OK";
    }
}
