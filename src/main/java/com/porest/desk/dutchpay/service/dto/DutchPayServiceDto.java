package com.porest.desk.dutchpay.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.DutchPayParticipant;
import com.porest.desk.dutchpay.type.SplitMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DutchPayServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long sourceExpenseRowId,
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        List<ParticipantCommand> participants
    ) {}

    public record UpdateCommand(
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        List<ParticipantCommand> participants
    ) {}

    public record ParticipantCommand(
        /**
         * 기존 참가자 행 아이디 — 있으면 제자리 수정, 없으면 신규.
         *
         * <p>이게 없으면 수정할 때마다 참가자를 통째로 지우고 새로 만들게 되고,
         * 그때 <b>정산 완료 표시(is_paid/paid_at)가 전부 풀린다</b>. 4명이 나눠 낸 회식비에서
         * 3명이 이미 입금해 체크해 뒀는데 금액 한 줄 고치면 그게 다 날아간다.
         */
        Long rowId,
        Long userRowId,
        String participantName,
        Long amount
    ) {}

    public record DutchPayInfo(
        Long rowId,
        Long userRowId,
        Long sourceExpenseRowId,
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        boolean isSettled,
        List<ParticipantInfo> participants,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static DutchPayInfo from(DutchPay dutchPay) {
            List<ParticipantInfo> participantInfos = dutchPay.getActiveParticipants().stream()
                .map(ParticipantInfo::from)
                .toList();
            return new DutchPayInfo(
                dutchPay.getRowId(),
                dutchPay.getUser().getRowId(),
                dutchPay.getSourceExpense() != null ? dutchPay.getSourceExpense().getRowId() : null,
                dutchPay.getTitle(),
                dutchPay.getDescription(),
                dutchPay.getTotalAmount(),
                dutchPay.getCurrency(),
                dutchPay.getSplitMethod(),
                dutchPay.getDutchPayDate(),
                dutchPay.getIsSettled() == YNType.Y,
                participantInfos,
                dutchPay.getCreateAt(),
                dutchPay.getModifyAt()
            );
        }
    }

    public record ParticipantInfo(
        Long rowId,
        Long userRowId,
        String participantName,
        Long amount,
        boolean isPaid,
        LocalDateTime paidAt
    ) {
        public static ParticipantInfo from(DutchPayParticipant participant) {
            return new ParticipantInfo(
                participant.getRowId(),
                participant.getUser() != null ? participant.getUser().getRowId() : null,
                participant.getParticipantName(),
                participant.getAmount(),
                participant.getIsPaid() == YNType.Y,
                participant.getPaidAt()
            );
        }
    }
}
