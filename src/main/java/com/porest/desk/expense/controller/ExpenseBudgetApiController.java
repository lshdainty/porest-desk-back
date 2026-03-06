package com.porest.desk.expense.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.expense.controller.dto.ExpenseBudgetApiDto;
import com.porest.desk.expense.service.ExpenseBudgetService;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExpenseBudgetApiController {
    private final ExpenseBudgetService expenseBudgetService;

    @PostMapping("/expense/budget")
    public ApiResponse<ExpenseBudgetApiDto.Response> createBudget(
            @LoginUser UserPrincipal loginUser,
            @RequestBody ExpenseBudgetApiDto.CreateRequest request) {
        ExpenseBudgetServiceDto.BudgetInfo info = expenseBudgetService.createBudget(new ExpenseBudgetServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.categoryRowId(),
            request.budgetAmount(),
            request.budgetYear(),
            request.budgetMonth()
        ));
        return ApiResponse.success(ExpenseBudgetApiDto.Response.from(info));
    }

    @GetMapping("/expense/budgets")
    public ApiResponse<ExpenseBudgetApiDto.ListResponse> getBudgets(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<ExpenseBudgetServiceDto.BudgetInfo> infos = expenseBudgetService.getBudgets(
            loginUser.getRowId(), year, month
        );
        return ApiResponse.success(ExpenseBudgetApiDto.ListResponse.from(infos));
    }

    @DeleteMapping("/expense/budget/{id}")
    public ApiResponse<Void> deleteBudget(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        expenseBudgetService.deleteBudget(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
