package com.porest.desk.subscription.gate;

import com.porest.desk.security.principal.JwtUserPrincipal;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 증권 기능권한 게이트. 등록된 경로(/api/v1/toss/**, 토스 크리덴셜)에 대해 활성 구독(SECURITIES)을 요구한다.
 * 미보유 시 {@code SUBSCRIPTION_REQUIRED(403)} — preHandle 예외는 GlobalExceptionHandler 가 처리한다.
 * 서버 권위 게이트(프론트/앱 메뉴 숨김은 UX, 우회 시 여기서 차단).
 */
@Component
@RequiredArgsConstructor
public class FeatureGateInterceptor implements HandlerInterceptor {

    private static final String FEATURE_SECURITIES = "SECURITIES";

    private final SubscriptionEntitlementService entitlementService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        entitlementService.requireFeature(currentUserRowId(), FEATURE_SECURITIES);
        return true;
    }

    private Long currentUserRowId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal principal) {
            return principal.getUserRowId();
        }
        return null;
    }
}
