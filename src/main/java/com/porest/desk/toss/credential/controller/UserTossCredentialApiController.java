package com.porest.desk.toss.credential.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.toss.credential.service.TossCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 사용자별 토스증권 크리덴셜 등록/상태/해제. 활성 구독(SECURITIES) 필요(FeatureGateInterceptor 가 게이트).
 * client_id/secret 은 요청 본문으로만 받고, 응답에는 secret/clientId 평문을 절대 반환하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/users/me/toss-credential")
@RequiredArgsConstructor
public class UserTossCredentialApiController {

    private final TossCredentialService credentialService;

    @PostMapping
    public ApiResponse<Void> register(
            @LoginUser UserPrincipal loginUser,
            @RequestBody RegisterRequest request) {
        credentialService.register(loginUser.getRowId(), request.clientId(), request.clientSecret());
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<CredentialStatusResponse> getStatus(@LoginUser UserPrincipal loginUser) {
        TossCredentialService.CredentialStatus status = credentialService.getStatus(loginUser.getRowId());
        return ApiResponse.success(
            new CredentialStatusResponse(status.connected(), status.verified(), status.verifiedAt()));
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect(@LoginUser UserPrincipal loginUser) {
        credentialService.disconnect(loginUser.getRowId());
        return ApiResponse.success();
    }

    public record RegisterRequest(String clientId, String clientSecret) {
    }

    public record CredentialStatusResponse(boolean connected, boolean verified, LocalDateTime verifiedAt) {
    }
}
