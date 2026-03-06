package com.porest.desk.expense.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.expense.controller.dto.ExpenseCategoryApiDto;
import com.porest.desk.expense.service.ExpenseCategoryService;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExpenseCategoryApiController {
    private final ExpenseCategoryService expenseCategoryService;

    @PostMapping("/expense/category")
    public ApiResponse<ExpenseCategoryApiDto.Response> createCategory(
            @LoginUser UserPrincipal loginUser,
            @RequestBody ExpenseCategoryApiDto.CreateRequest request) {
        ExpenseCategoryServiceDto.CategoryInfo info = expenseCategoryService.createCategory(new ExpenseCategoryServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.categoryName(),
            request.icon(),
            request.color(),
            request.expenseType(),
            request.parentRowId()
        ));
        return ApiResponse.success(ExpenseCategoryApiDto.Response.from(info));
    }

    @GetMapping("/expense/categories")
    public ApiResponse<ExpenseCategoryApiDto.ListResponse> getCategories(
            @LoginUser UserPrincipal loginUser) {
        List<ExpenseCategoryServiceDto.CategoryInfo> infos = expenseCategoryService.getCategories(loginUser.getRowId());
        return ApiResponse.success(ExpenseCategoryApiDto.ListResponse.from(infos));
    }

    @PutMapping("/expense/category/{id}")
    public ApiResponse<ExpenseCategoryApiDto.Response> updateCategory(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody ExpenseCategoryApiDto.UpdateRequest request) {
        ExpenseCategoryServiceDto.CategoryInfo info = expenseCategoryService.updateCategory(id, loginUser.getRowId(), new ExpenseCategoryServiceDto.UpdateCommand(
            request.categoryName(),
            request.icon(),
            request.color(),
            request.sortOrder()
        ));
        return ApiResponse.success(ExpenseCategoryApiDto.Response.from(info));
    }

    @DeleteMapping("/expense/category/{id}")
    public ApiResponse<Void> deleteCategory(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        expenseCategoryService.deleteCategory(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
