package com.porest.desk.common.config.web;

import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.subscription.gate.FeatureGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    // 게이트 인터셉터는 선택 주입 — @WebMvcTest 슬라이스(서비스/리포지토리 미로드)에서도 WebConfig 가 깨지지 않게.
    private final ObjectProvider<FeatureGateInterceptor> featureGateInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 증권 기능권한 게이트 — 토스 데이터·크리덴셜은 활성 구독(SECURITIES) 필요. 구독 API 는 제외.
        FeatureGateInterceptor interceptor = featureGateInterceptor.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/toss/**",
                        "/api/v1/namu/**",
                        // 구버전 앱이 쓰는 옛 경로 — 게이트에서 빠지면 비구독자가 통과한다
                        "/api/v1/users/me/toss-credential",
                        "/api/v1/users/me/securities-credentials",
                        "/api/v1/users/me/securities-credentials/**");
        }
    }
}
