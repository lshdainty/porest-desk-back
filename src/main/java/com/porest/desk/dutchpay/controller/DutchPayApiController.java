package com.porest.desk.dutchpay.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.dutchpay.controller.dto.DutchPayApiDto;
import com.porest.desk.dutchpay.service.DutchPayService;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class DutchPayApiController {
    private final DutchPayService dutchPayService;

    @PostMapping("/dutch-pay")
    public ApiResponse<DutchPayApiDto.Response> createDutchPay(
            @LoginUser UserPrincipal loginUser,
            @RequestBody DutchPayApiDto.CreateRequest request) {
        List<DutchPayServiceDto.ParticipantCommand> participants = request.participants() != null
            ? request.participants().stream()
                .map(p -> new DutchPayServiceDto.ParticipantCommand(p.participantName(), p.amount()))
                .toList()
            : List.of();

        DutchPayServiceDto.DutchPayInfo info = dutchPayService.createDutchPay(new DutchPayServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.title(),
            request.description(),
            request.totalAmount(),
            request.currency(),
            request.splitMethod(),
            request.dutchPayDate(),
            participants
        ));
        return ApiResponse.success(DutchPayApiDto.Response.from(info));
    }

    @GetMapping("/dutch-pays")
    public ApiResponse<DutchPayApiDto.ListResponse> getDutchPays(
            @LoginUser UserPrincipal loginUser) {
        List<DutchPayServiceDto.DutchPayInfo> infos = dutchPayService.getDutchPays(loginUser.getRowId());
        return ApiResponse.success(DutchPayApiDto.ListResponse.from(infos));
    }

    @GetMapping("/dutch-pay/{id}")
    public ApiResponse<DutchPayApiDto.Response> getDutchPay(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        DutchPayServiceDto.DutchPayInfo info = dutchPayService.getDutchPay(id, loginUser.getRowId());
        return ApiResponse.success(DutchPayApiDto.Response.from(info));
    }

    @PutMapping("/dutch-pay/{id}")
    public ApiResponse<DutchPayApiDto.Response> updateDutchPay(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody DutchPayApiDto.UpdateRequest request) {
        List<DutchPayServiceDto.ParticipantCommand> participants = request.participants() != null
            ? request.participants().stream()
                .map(p -> new DutchPayServiceDto.ParticipantCommand(p.participantName(), p.amount()))
                .toList()
            : List.of();

        DutchPayServiceDto.DutchPayInfo info = dutchPayService.updateDutchPay(id, loginUser.getRowId(), new DutchPayServiceDto.UpdateCommand(
            request.title(),
            request.description(),
            request.totalAmount(),
            request.currency(),
            request.splitMethod(),
            request.dutchPayDate(),
            participants
        ));
        return ApiResponse.success(DutchPayApiDto.Response.from(info));
    }

    @DeleteMapping("/dutch-pay/{id}")
    public ApiResponse<Void> deleteDutchPay(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        dutchPayService.deleteDutchPay(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    @PatchMapping("/dutch-pay/{id}/participant/{participantId}/paid")
    public ApiResponse<DutchPayApiDto.Response> markParticipantPaid(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @PathVariable Long participantId) {
        DutchPayServiceDto.DutchPayInfo info = dutchPayService.markParticipantPaid(id, loginUser.getRowId(), participantId);
        return ApiResponse.success(DutchPayApiDto.Response.from(info));
    }

    @PatchMapping("/dutch-pay/{id}/settle")
    public ApiResponse<DutchPayApiDto.Response> settleAll(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        DutchPayServiceDto.DutchPayInfo info = dutchPayService.settleAll(id, loginUser.getRowId());
        return ApiResponse.success(DutchPayApiDto.Response.from(info));
    }
}
