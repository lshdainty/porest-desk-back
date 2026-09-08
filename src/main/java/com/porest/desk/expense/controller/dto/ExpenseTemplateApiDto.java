package com.porest.desk.expense.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class ExpenseTemplateApiDto {

    @Schema(name = "ExpenseTemplateCreateRequest")
    public record CreateRequest(
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer sortOrder,
        YNType lockAmount
    ) {}

    /**
     * 수정 본문 — <b>실린 칸만 바꾼다</b>(QA #96 의 계약을 프리셋으로 넓힌다).
     *
     * <p>{@code Optional} 참조가 {@code null} 이면 키가 없었던 것(유지),
     * {@code Optional.empty()} 면 {@code null} 이 실린 것(지움)이다
     * ({@code AbsentAwareOptionalModule}).
     *
     * <p>이 칸을 바꾼 계기는 {@code description} 이다. <b>웹·앱 어느 편집 화면도 이 키를 싣지
     * 않는데</b> 등록({@code POST})은 받고 웹 상세는 그 값을 그린다 — 저장된 메모가 있고, 보이고,
     * 편집 한 번에 사라지고, 되살릴 입력칸이 어디에도 없었다. {@code merchant}·
     * {@code paymentMethod}·{@code assetRowId} 도 같은 모양이라 한 칸만 고치면 다음 사람이
     * 나머지에서 같은 자리를 다시 밟는다 — {@code ExpenseApiDto.UpdateRequest} 와 같은 뜻으로
     * 통째로 맞춘다.
     *
     * <p>NOT NULL 칸({@code templateName}·{@code expenseType}·{@code lockAmount})은 "지운다" 가
     * 성립하지 않는다. 안 보내면 지금 값을 지키고, 명시적 {@code null} 은 400 이다
     * (이름은 {@code NameNormalizer}, 나머지는 도메인 가드가 끊는다).
     *
     * <p>{@code sortOrder} 는 종전대로 여기에 없다 — 순서는 전용 경로가 정한다.
     */
    @Schema(name = "ExpenseTemplateUpdateRequest")
    public record UpdateRequest(
        Optional<String> templateName,
        Optional<Long> categoryRowId,
        Optional<Long> assetRowId,
        Optional<ExpenseType> expenseType,
        Optional<Long> amount,
        Optional<String> description,
        Optional<String> merchant,
        Optional<String> paymentMethod,
        Optional<YNType> lockAmount
    ) {}

    public record UseRequest(
        LocalDate expenseDate
    ) {}

    @Schema(name = "ExpenseTemplateResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String templateName,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer useCount,
        Integer sortOrder,
        YNType lockAmount,
        LocalDateTime lastUsedAt,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseTemplateServiceDto.TemplateInfo info) {
            return new Response(
                info.rowId(), info.userRowId(), info.templateName(),
                info.categoryRowId(), info.categoryName(),
                info.assetRowId(), info.assetName(),
                info.expenseType(), info.amount(), info.description(),
                info.merchant(), info.paymentMethod(),
                info.useCount(), info.sortOrder(),
                info.lockAmount(), info.lastUsedAt(),
                info.createAt(), info.modifyAt()
            );
        }
    }

    @Schema(name = "ExpenseTemplateListResponse")
    public record ListResponse(List<Response> templates) {
        public static ListResponse from(List<ExpenseTemplateServiceDto.TemplateInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
