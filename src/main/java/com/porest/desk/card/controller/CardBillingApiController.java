package com.porest.desk.card.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.card.controller.dto.CardBillingApiDto;
import com.porest.desk.card.service.CardPaymentService;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CardBillingApiController {
    private final CardPaymentService cardPaymentService;

    /** 카드 청구 조회 — 현재 사이클 예정액 + 다음 결제예정일 + 과거 청구 이력. */
    @GetMapping("/asset/{id}/billing")
    public ApiResponse<CardBillingApiDto.CardBillingResponse> getCardBilling(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        CardPaymentServiceDto.CardBillingInfo info =
            cardPaymentService.getCardBilling(id, loginUser.getRowId());
        return ApiResponse.success(CardBillingApiDto.CardBillingResponse.from(info));
    }

    /** 카드 수동 결제 — 결제계좌에서 카드로 이체하여 잔액을 0으로 복귀. */
    @PostMapping("/asset/{id}/pay")
    public ApiResponse<CardBillingApiDto.BillingItemResponse> payCard(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        CardPaymentServiceDto.BillingInfo info =
            cardPaymentService.payCard(id, loginUser.getRowId());
        return ApiResponse.success(CardBillingApiDto.BillingItemResponse.from(info));
    }
}
