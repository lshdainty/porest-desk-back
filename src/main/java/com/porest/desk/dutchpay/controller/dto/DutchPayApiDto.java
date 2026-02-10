package com.porest.desk.dutchpay.controller.dto;

import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DutchPayApiDto {

    public record CreateRequest(
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        List<ParticipantRequest> participants
    ) {}

    public record UpdateRequest(
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        List<ParticipantRequest> participants
    ) {}

    public record ParticipantRequest(
        String participantName,
        Long amount
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        boolean isSettled,
        List<ParticipantResponse> participants,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(DutchPayServiceDto.DutchPayInfo info) {
            List<ParticipantResponse> participantResponses = info.participants().stream()
                .map(ParticipantResponse::from)
                .toList();
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.description(),
                info.totalAmount(),
                info.currency(),
                info.splitMethod(),
                info.dutchPayDate(),
                info.isSettled(),
                participantResponses,
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ParticipantResponse(
        Long rowId,
        String participantName,
        Long amount,
        boolean isPaid,
        LocalDateTime paidAt
    ) {
        public static ParticipantResponse from(DutchPayServiceDto.ParticipantInfo info) {
            return new ParticipantResponse(
                info.rowId(),
                info.participantName(),
                info.amount(),
                info.isPaid(),
                info.paidAt()
            );
        }
    }

    public record ListResponse(
        List<Response> dutchPays
    ) {
        public static ListResponse from(List<DutchPayServiceDto.DutchPayInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
