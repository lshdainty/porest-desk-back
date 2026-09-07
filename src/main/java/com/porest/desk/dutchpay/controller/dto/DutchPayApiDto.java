package com.porest.desk.dutchpay.controller.dto;

import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DutchPayApiDto {

    /**
     * 정산은 <b>참가자가 한 명은 있어야</b> 하고 총액은 0보다 커야 한다(QA 2026-09-07 #86).
     *
     * <p>종전엔 {@code participants} 를 빈 배열로 보내거나 아예 빼도 200 이었다 — 누가 얼마를
     * 나눠 내는지가 없는 정산 행이 남고, 목록은 1/N 을 계산할 사람이 없어 0원으로 보인다.
     * 총액 {@code -1} 도 200 이었다: 받을 돈이 음수인 정산이 저장돼 합계·미정산 금액이 거꾸로 간다.
     *
     * <p>웹·앱 모두 만들기 화면이 <b>두 명 이상</b>을 골라야 다음으로 넘어가고 총액도 0보다 커야
     * 하므로, 여기서 한 명·1원으로 끊어도 쓰던 화면이 막히지 않는다(2026-09-07 양쪽 코드로 확인).
     *
     * <p><b>결제자가 몇 명인지는 여기서 안 본다</b> — 그건 별도 항목(#80)이다. 이 자리는
     * 참가자 <b>개수</b>와 금액만 본다.
     */
    @Schema(name = "DutchPayCreateRequest")
    public record CreateRequest(
        Long sourceExpenseRowId,
        String title,
        String description,
        @Min(value = 1, message = "금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long totalAmount,
        String currency,
        SplitMethod splitMethod,
        LocalDate dutchPayDate,
        @NotEmpty(message = "함께 나눌 사람을 한 명 이상 골라 주세요")
        List<ParticipantRequest> participants
    ) {}

    /**
     * 수정은 {@code participants} 를 <b>안 보낼 수 있다</b> — "참가자는 안 건드린다" 는 뜻이라
     * {@code @NotEmpty} 를 걸면 제목만 고치는 요청이 막힌다. 빈 배열을 명시해 전원을 지우는
     * 경우는 결제자가 0명이 되는 문제와 같은 자리라 별도 항목(#80)에서 다룬다.
     */
    @Schema(name = "DutchPayUpdateRequest")
    public record UpdateRequest(
        String title,
        String description,
        @Min(value = 1, message = "금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
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
