package com.porest.desk.calculator.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calculator.controller.dto.CalculatorHistoryApiDto;
import com.porest.desk.calculator.service.CalculatorHistoryService;
import com.porest.desk.calculator.service.dto.CalculatorHistoryServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CalculatorHistoryApiController {
    private final CalculatorHistoryService calculatorHistoryService;

    @PostMapping("/calculator/history")
    public ApiResponse<CalculatorHistoryApiDto.Response> createHistory(
            @LoginUser UserPrincipal loginUser,
            @RequestBody CalculatorHistoryApiDto.CreateRequest request) {
        CalculatorHistoryServiceDto.HistoryInfo info = calculatorHistoryService.createHistory(
            new CalculatorHistoryServiceDto.CreateCommand(
                loginUser.getRowId(),
                request.expression(),
                request.result()
            )
        );
        return ApiResponse.success(CalculatorHistoryApiDto.Response.from(info));
    }

    @GetMapping("/calculator/histories")
    public ApiResponse<CalculatorHistoryApiDto.ListResponse> getHistories(
            @LoginUser UserPrincipal loginUser) {
        List<CalculatorHistoryServiceDto.HistoryInfo> infos = calculatorHistoryService.getHistories(
            loginUser.getRowId()
        );
        return ApiResponse.success(CalculatorHistoryApiDto.ListResponse.from(infos));
    }

    @DeleteMapping("/calculator/histories")
    public ApiResponse<Void> deleteAllHistories(
            @LoginUser UserPrincipal loginUser) {
        calculatorHistoryService.deleteAllHistories(loginUser.getRowId());
        return ApiResponse.success();
    }
}
