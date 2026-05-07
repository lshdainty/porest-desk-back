package com.porest.desk.expense.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.expense.controller.dto.ExpenseApiDto;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExpenseApiController {
    private final ExpenseService expenseService;

    @PostMapping("/expense")
    public ApiResponse<ExpenseApiDto.Response> createExpense(
            @LoginUser UserPrincipal loginUser,
            @RequestBody ExpenseApiDto.CreateRequest request) {
        ExpenseServiceDto.ExpenseInfo info = expenseService.createExpense(new ExpenseServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.categoryRowId(),
            request.assetRowId(),
            request.expenseType(),
            request.amount(),
            request.description(),
            parseExpenseDate(request.expenseDate()),
            request.merchant(),
            request.paymentMethod(),
            request.calendarEventRowId(),
            request.todoRowId(),
            request.groupRowId()
        ));
        return ApiResponse.success(ExpenseApiDto.Response.from(info));
    }

    @GetMapping("/expenses")
    public ApiResponse<ExpenseApiDto.ListResponse> getExpenses(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) ExpenseType expenseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.getExpenses(
            loginUser.getRowId(), categoryId, assetId, expenseType, startDate, endDate
        );
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    @GetMapping("/group/{groupId}/expenses")
    public ApiResponse<ExpenseApiDto.ListResponse> getGroupExpenses(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long groupId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ExpenseType expenseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.getGroupExpenses(
            loginUser.getRowId(), groupId, categoryId, expenseType, startDate, endDate
        );
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    @PutMapping("/expense/{id}")
    public ApiResponse<ExpenseApiDto.Response> updateExpense(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody ExpenseApiDto.UpdateRequest request) {
        ExpenseServiceDto.ExpenseInfo info = expenseService.updateExpense(id, loginUser.getRowId(), new ExpenseServiceDto.UpdateCommand(
            request.categoryRowId(),
            request.assetRowId(),
            request.expenseType(),
            request.amount(),
            request.description(),
            parseExpenseDate(request.expenseDate()),
            request.merchant(),
            request.paymentMethod(),
            request.calendarEventRowId(),
            request.todoRowId(),
            request.groupRowId()
        ));
        return ApiResponse.success(ExpenseApiDto.Response.from(info));
    }

    @DeleteMapping("/expense/{id}")
    public ApiResponse<Void> deleteExpense(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        expenseService.deleteExpense(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    @GetMapping("/expenses/summary/daily")
    public ApiResponse<ExpenseApiDto.DailySummaryResponse> getDailySummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExpenseServiceDto.DailySummary summary = expenseService.getDailySummary(loginUser.getRowId(), date);
        return ApiResponse.success(ExpenseApiDto.DailySummaryResponse.from(summary));
    }

    @GetMapping("/expenses/summary/range")
    public ApiResponse<ExpenseApiDto.RangeSummaryResponse> getRangeSummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ExpenseServiceDto.RangeSummary summary = expenseService.getRangeSummary(loginUser.getRowId(), startDate, endDate);
        return ApiResponse.success(ExpenseApiDto.RangeSummaryResponse.from(summary));
    }

    @GetMapping("/expenses/summary/trend")
    public ApiResponse<ExpenseApiDto.MonthlyTrendListResponse> getMonthlyTrend(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false, defaultValue = "6") Integer months) {
        List<ExpenseServiceDto.MonthlyTrend> trends = expenseService.getMonthlyTrend(loginUser.getRowId(), months);
        return ApiResponse.success(ExpenseApiDto.MonthlyTrendListResponse.from(trends));
    }

    @GetMapping("/expenses/summary/by-merchant")
    public ApiResponse<ExpenseApiDto.MerchantSummaryListResponse> getMerchantSummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.MerchantSummary> summaries = expenseService.getMerchantSummary(loginUser.getRowId(), startDate, endDate);
        return ApiResponse.success(ExpenseApiDto.MerchantSummaryListResponse.from(summaries));
    }

    @GetMapping("/expenses/summary/by-asset")
    public ApiResponse<ExpenseApiDto.AssetSummaryListResponse> getAssetSummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.AssetSummary> summaries = expenseService.getAssetSummary(loginUser.getRowId(), startDate, endDate);
        return ApiResponse.success(ExpenseApiDto.AssetSummaryListResponse.from(summaries));
    }

    @GetMapping("/calendar/event/{eventId}/expenses")
    public ApiResponse<ExpenseApiDto.ListResponse> getExpensesByCalendarEvent(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long eventId) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.getExpensesByCalendarEvent(eventId);
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    @GetMapping("/todo/{todoId}/expenses")
    public ApiResponse<ExpenseApiDto.ListResponse> getExpensesByTodo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long todoId) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.getExpensesByTodo(todoId);
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    @GetMapping("/expenses/summary/heatmap")
    public ApiResponse<ExpenseApiDto.HeatmapResponse> getHeatmap(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.HeatmapCell> cells = expenseService.getHeatmap(loginUser.getRowId(), startDate, endDate);
        return ApiResponse.success(ExpenseApiDto.HeatmapResponse.from(cells));
    }

    @GetMapping("/expenses/search")
    public ApiResponse<ExpenseApiDto.ListResponse> searchExpenses(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) ExpenseType expenseType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) Long minAmount,
            @RequestParam(required = false) Long maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.searchExpenses(new ExpenseServiceDto.SearchCommand(
            loginUser.getRowId(), categoryId, assetId, expenseType, keyword, merchant,
            minAmount, maxAmount, startDate, endDate
        ));
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    /**
     * expenseDate 문자열을 LocalDateTime 으로 유연 파싱.
     * - "yyyy-MM-dd"        → 해당 일자 00:00:00
     * - "yyyy-MM-ddTHH:mm"  → ISO_LOCAL_DATE_TIME (초 생략 허용)
     * - "yyyy-MM-dd HH:mm"  → 공백 구분자 허용
     * - null/빈 문자열은 null 반환
     */
    private static LocalDateTime parseExpenseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }
        // 공백 구분자를 ISO_LOCAL_DATE_TIME 호환으로 치환
        String normalized = trimmed.replace(' ', 'T');
        return LocalDateTime.parse(normalized);
    }
}
