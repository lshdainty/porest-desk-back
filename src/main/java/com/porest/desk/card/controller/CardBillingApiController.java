package com.porest.desk.card.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.card.controller.dto.CardBillingApiDto;
import com.porest.desk.card.service.CardPaymentService;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 카드 수동 결제 — 결제계좌에서 카드로 이체하여 사용액을 정리한다.
     * 결제계좌가 없으면 이체 없이 카드 쪽만 정리한다(기록용 앱이라 통장을 안 적는 사용자가 있다).
     *
     * @param amount 결제 금액. 미전달이면 남은 청구액 전액, 값이 있으면 그만큼만(부분 선결제)
     */
    @PostMapping("/asset/{id}/pay")
    public ApiResponse<CardBillingApiDto.BillingItemResponse> payCard(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestParam(required = false) Long amount) {
        CardPaymentServiceDto.BillingInfo info =
            cardPaymentService.payCard(id, loginUser.getRowId(), amount);
        return ApiResponse.success(CardBillingApiDto.BillingItemResponse.from(info));
    }

    /**
     * 카드 결제 취소 — 결제로 만든 이체를 무르고 청구 회차를 되돌린다.
     *
     * <p>잘못 누른 결제를 되돌릴 길이 없었다. 그 이체는 청구와 묶여 있어 사용자가 직접
     * 지울 수 없게 잠가 뒀는데(그래야 따로 놀지 않는다), 취소 경로도 없어 영구적이었다.
     */
    @DeleteMapping("/card-billing/{billingId}")
    public ApiResponse<Void> cancelPayment(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long billingId) {
        cardPaymentService.cancelPayment(billingId, loginUser.getRowId());
        return ApiResponse.success(null);
    }
}
