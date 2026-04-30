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
            List<ParticipantInfo> participantInfos = dutchPay.getParticipants().stream()
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
