package com.porest.desk.dutchpay.controller.dto;

import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DutchPayApiDto {

    @Schema(name = "DutchPayCreateRequest")
    public record CreateRequest(
        Long sourceExpenseRowId,
        String title,
        String description,
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        List<ParticipantRequest> participants
    ) {}

    @Schema(name = "DutchPayUpdateRequest")
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
        /** 기존 참가자 행 아이디 — 보내면 제자리 수정돼 정산 완료 표시가 유지된다. */
        Long rowId,
        Long userRowId,
        String participantName,
        Long amount,
        /**
         * 이 사람이 결제했는가. 한 정산에 한 명이다.
         *
         * <p>nullable 이다 — 이 필드를 모르는 구버전 앱이 여전히 정산을 만들 수 있어야 한다.
         * 아무도 표시돼 있지 않으면 서버가 첫 사람을 결제자로 본다.
         */
        Boolean isPayer
    ) {}

    @Schema(name = "DutchPayResponse")
    public record Response(
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
                info.sourceExpenseRowId(),
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
        Long userRowId,
        String participantName,
        Long amount,
        boolean isPayer,
        boolean isPaid,
        LocalDateTime paidAt
    ) {
        public static ParticipantResponse from(DutchPayServiceDto.ParticipantInfo info) {
            return new ParticipantResponse(
                info.rowId(),
                info.userRowId(),
                info.participantName(),
                info.amount(),
                info.isPayer(),
                info.isPaid(),
                info.paidAt()
            );
        }
    }

    @Schema(name = "DutchPayListResponse")
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
