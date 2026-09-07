package com.porest.desk.dutchpay.controller.dto;

import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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
     * {@code @NotEmpty} 를 걸면 제목만 고치는 요청이 막힌다. 그래서 {@code @Size(min = 1)} 이다:
     * {@code null}(키 없음)은 통과시키고 <b>빈 배열만</b> 막는다. 둘은 서로 다른 요청이다.
     *
     * <p>빈 배열을 받아 주던 종전 동작은 활성 참가자를 전원 지웠다 — 그러면 <b>결제자도 0명</b>이
     * 되고, 결제자가 없는 정산은 {@code getDebtors()} 가 전원을 갚을 사람으로 돌려줘 전체 정산이
     * 결제자까지 납부 처리한다. 결제자를 반드시 한 명 두기로 한 이상(QA 2026-09-07 #80) 참가자를
     * 0명으로 만드는 요청도 같은 자리에서 막아야 한다.
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
        @Size(min = 1, message = "함께 나눌 사람을 한 명 이상 골라 주세요")
        List<ParticipantRequest> participants
    ) {}

    public record ParticipantRequest(
        /** 기존 참가자 행 아이디 — 보내면 제자리 수정돼 정산 완료 표시가 유지된다. */
        Long rowId,
        Long userRowId,
        String participantName,
        Long amount,
        /**
         * 이 사람이 결제했는가. 한 정산에 <b>반드시 한 명</b>이다(QA 2026-09-07 #80).
         *
         * <p>nullable 인 것은 <b>키가 왔는지</b>를 봐야 하기 때문이다. {@code Boolean} 이라
         * Jackson 이 세 가지를 구분해 넘긴다 — 키 없음·{@code null} 은 {@code null},
         * {@code false} 는 {@code FALSE}. 서버는 이 차이로 <b>보낸 쪽이 이 필드를 아는지</b>를
         * 가른다: 목록 어디에도 키가 없으면 구버전 클라이언트라 첫 사람을 결제자로 보고,
         * 한 명이라도 키를 실었으면 아는 클라이언트라 정확히 한 명이 {@code true} 여야 한다.
         * {@code boolean} 으로 바꾸면 키 없음이 {@code false} 로 뭉개져 이 구분이 사라진다.
         *
         * <p>지금 쓰는 클라이언트는 둘 다 <b>모든 참가자에</b> 이 키를 싣는다 — 웹은 새로고침이
         * 곧 최신이라 항상 그렇고, 앱은 v1.12.0(2026-08-14)부터다. "가끔 싣는" 중간 버전은
         * 없다: 양쪽 다 한 커밋에서 전원에 한꺼번에 붙였다(2026-09-07 양쪽 이력으로 확인).
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
