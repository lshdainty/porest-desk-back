package com.porest.desk.expense.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.expense.controller.dto.ExpenseApiDto;
import com.porest.desk.expense.controller.dto.ExpenseTemplateApiDto;
import com.porest.desk.expense.service.ExpenseTemplateService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
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
public class ExpenseTemplateApiController {
    private final ExpenseTemplateService expenseTemplateService;

    @PostMapping("/expense-template")
    public ApiResponse<ExpenseTemplateApiDto.Response> createTemplate(
            @LoginUser UserPrincipal loginUser,
            @RequestBody ExpenseTemplateApiDto.CreateRequest request) {
        ExpenseTemplateServiceDto.TemplateInfo info = expenseTemplateService.createTemplate(
            new ExpenseTemplateServiceDto.CreateCommand(
                loginUser.getRowId(),
                request.templateName(), request.categoryRowId(), request.assetRowId(),
                request.expenseType(), request.amount(), request.description(),
                request.merchant(), request.paymentMethod(), request.sortOrder()
            )
        );
        return ApiResponse.success(ExpenseTemplateApiDto.Response.from(info));
    }

    @GetMapping("/expense-templates")
    public ApiResponse<ExpenseTemplateApiDto.ListResponse> getTemplates(@LoginUser UserPrincipal loginUser) {
        List<ExpenseTemplateServiceDto.TemplateInfo> infos = expenseTemplateService.getTemplates(loginUser.getRowId());
        return ApiResponse.success(ExpenseTemplateApiDto.ListResponse.from(infos));
    }

    @PutMapping("/expense-template/{id}")
    public ApiResponse<ExpenseTemplateApiDto.Response> updateTemplate(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody ExpenseTemplateApiDto.UpdateRequest request) {
        ExpenseTemplateServiceDto.TemplateInfo info = expenseTemplateService.updateTemplate(id, loginUser.getRowId(),
            new ExpenseTemplateServiceDto.UpdateCommand(
                request.templateName(), request.categoryRowId(), request.assetRowId(),
                request.expenseType(), request.amount(), request.description(),
                request.merchant(), request.paymentMethod()
            )
        );
        return ApiResponse.success(ExpenseTemplateApiDto.Response.from(info));
    }

    @DeleteMapping("/expense-template/{id}")
    public ApiResponse<Void> deleteTemplate(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        expenseTemplateService.deleteTemplate(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    @PostMapping("/expense-template/{id}/use")
    public ApiResponse<ExpenseApiDto.Response> useTemplate(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody ExpenseTemplateApiDto.UseRequest request) {
        ExpenseServiceDto.ExpenseInfo info = expenseTemplateService.useTemplate(id, loginUser.getRowId(), request.expenseDate());
        return ApiResponse.success(ExpenseApiDto.Response.from(info));
    }
}
