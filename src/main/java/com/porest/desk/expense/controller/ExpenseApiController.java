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
            request.expenseType(),
            request.amount(),
            request.description(),
            request.expenseDate(),
            request.paymentMethod()
        ));
        return ApiResponse.success(ExpenseApiDto.Response.from(info));
    }

    @GetMapping("/expenses")
    public ApiResponse<ExpenseApiDto.ListResponse> getExpenses(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ExpenseType expenseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ExpenseServiceDto.ExpenseInfo> infos = expenseService.getExpenses(
            loginUser.getRowId(), categoryId, expenseType, startDate, endDate
        );
        return ApiResponse.success(ExpenseApiDto.ListResponse.from(infos));
    }

    @PutMapping("/expense/{id}")
    public ApiResponse<ExpenseApiDto.Response> updateExpense(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody ExpenseApiDto.UpdateRequest request) {
        ExpenseServiceDto.ExpenseInfo info = expenseService.updateExpense(id, new ExpenseServiceDto.UpdateCommand(
            request.categoryRowId(),
            request.expenseType(),
            request.amount(),
            request.description(),
            request.expenseDate(),
            request.paymentMethod()
        ));
        return ApiResponse.success(ExpenseApiDto.Response.from(info));
    }

    @DeleteMapping("/expense/{id}")
    public ApiResponse<Void> deleteExpense(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ApiResponse.success();
    }

    @GetMapping("/expenses/summary/daily")
    public ApiResponse<ExpenseApiDto.DailySummaryResponse> getDailySummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExpenseServiceDto.DailySummary summary = expenseService.getDailySummary(loginUser.getRowId(), date);
        return ApiResponse.success(ExpenseApiDto.DailySummaryResponse.from(summary));
    }

    @GetMapping("/expenses/summary/monthly")
    public ApiResponse<ExpenseApiDto.MonthlySummaryResponse> getMonthlySummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        ExpenseServiceDto.MonthlySummary summary = expenseService.getMonthlySummary(loginUser.getRowId(), year, month);
        return ApiResponse.success(ExpenseApiDto.MonthlySummaryResponse.from(summary));
    }
}
