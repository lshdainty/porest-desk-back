package com.porest.desk.expense.controller.dto;

import com.porest.desk.common.validation.ColorFormat;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseCategoryApiDto {

    /**
     * {@code expenseType} 은 <b>있어야 한다</b> — 없으면 종전엔 500 이었다(QA 2026-09-07 #85).
     * 중복 이름 검사가 {@code expenseType} 으로 걸러 조회하는데, QueryDSL 은
     * {@code eq(null)} 을 {@code IllegalArgumentException} 으로 거절하고 그것이
     * {@code @Repository} 프록시에서 {@code InvalidDataAccessApiUsageException} 으로
     * 번역돼 매핑이 없는 채로 500 이 됐다(H2 로 재현).
     *
     * <p>웹은 {@code ExpenseCategoryFormValues.expenseType} 이 필수 필드고 앱도
     * {@code required String expenseType} 이라 필수로 걸어도 쓰던 화면이 막히지 않는다
     * (2026-09-07 양쪽 코드로 확인).
     */
    @Schema(name = "ExpenseCategoryCreateRequest")
    public record CreateRequest(
        String categoryName,
        String icon,
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color,
        @NotNull(message = "지출인지 수입인지 골라 주세요")
        ExpenseType expenseType,
        /**
         * 목록에서의 자리 — 안 보내면 0(맨 앞)이다. 종전엔 이 필드가 아예 없어 웹이 실어 보내도
         * Jackson 이 조용히 버렸고, 만들자마자 순서가 흐트러졌다(QA 2026-09-07 #91).
         * 순서 변경 API(reorder)와 같은 값을 쓴다 — 거기도 범위 제한이 없으므로 여기도 걸지 않는다.
         */
        Integer sortOrder,
        Long parentRowId
    ) {}

    @Schema(name = "ExpenseCategoryUpdateRequest")
    public record UpdateRequest(
        String categoryName,
        String icon,
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color,
        // null 이면 변경 없음 (클라이언트가 보낸 경우에만 반영)
        ExpenseType expenseType,
        Integer sortOrder,
        // null = 최상위로 이동. 웹/앱 편집 다이얼로그는 항상 이 필드를 포함해 전송.
        Long parentRowId
    ) {}

    @Schema(name = "ExpenseCategoryResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String categoryName,
        String icon,
        String color,
        ExpenseType expenseType,
        Integer sortOrder,
        Long parentRowId,
        boolean hasChildren,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseCategoryServiceDto.CategoryInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryName(),
                info.icon(),
                info.color(),
                info.expenseType(),
                info.sortOrder(),
                info.parentRowId(),
                info.hasChildren(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @Schema(name = "ExpenseCategoryListResponse")
    public record ListResponse(
        List<Response> categories
    ) {
        public static ListResponse from(List<ExpenseCategoryServiceDto.CategoryInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }

    @Schema(name = "ExpenseCategoryReorderItem")
    public record ReorderItem(
        Long categoryRowId,
        Integer sortOrder,
        Long parentRowId
    ) {}

    @Schema(name = "ExpenseCategoryReorderRequest")
    public record ReorderRequest(List<ReorderItem> items) {}

    /** 일괄 이동 요청 — 옮길 대상 카테고리. */
    public record MoveRequest(Long targetCategoryRowId) {}

    /** 하위 생성 + 거래 이동 요청. */
    public record SplitIntoChildRequest(
        String childName,
        String icon,
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color
    ) {}

    /** 일괄 이동 결과 — 무엇이 몇 건 옮겨졌는지. */
    public record MoveResponse(int expenses, int recurring, int splits) {}
}
