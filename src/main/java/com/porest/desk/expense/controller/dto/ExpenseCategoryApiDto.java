package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseCategoryApiDto {

    @Schema(name = "ExpenseCategoryCreateRequest")
    public record CreateRequest(
        String categoryName,
        String icon,
        String color,
        ExpenseType expenseType,
        Long parentRowId
    ) {}

    @Schema(name = "ExpenseCategoryUpdateRequest")
    public record UpdateRequest(
        String categoryName,
        String icon,
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
    public record SplitIntoChildRequest(String childName, String icon, String color) {}

    /** 일괄 이동 결과 — 무엇이 몇 건 옮겨졌는지. */
    public record MoveResponse(int expenses, int recurring, int splits) {}
}
