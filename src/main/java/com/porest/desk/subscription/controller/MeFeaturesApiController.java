package com.porest.desk.subscription.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import com.porest.desk.toss.credential.service.TossCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 현재 사용자의 기능권한·연결상태. 프론트/앱 메뉴 게이트 + 설정 노출의 단일 소스.
 * 게이트 미적용 — 권한 판단용이므로 인증 사용자 누구나 호출(비구독자는 features 빈 배열).
 */
@RestController
@RequestMapping("/api/v1/users/me/features")
@RequiredArgsConstructor
public class MeFeaturesApiController {

    private final SubscriptionEntitlementService entitlementService;
    private final TossCredentialService credentialService;

    @GetMapping
    public ApiResponse<MeFeaturesResponse> getMyFeatures(@LoginUser UserPrincipal loginUser) {
        Long userRowId = loginUser.getRowId();
        List<String> features = entitlementService.getActiveFeatures(userRowId);
        boolean tossConnected = credentialService.getStatus(userRowId).connected();
        return ApiResponse.success(new MeFeaturesResponse(features, tossConnected));
    }

    public record MeFeaturesResponse(List<String> features, boolean tossConnected) {
    }
}
