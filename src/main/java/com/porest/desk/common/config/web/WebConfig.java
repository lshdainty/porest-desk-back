package com.porest.desk.common.config.web;

import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.subscription.gate.FeatureGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final FeatureGateInterceptor featureGateInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 증권 기능권한 게이트 — 토스 데이터·크리덴셜은 활성 구독(SECURITIES) 필요. 구독 API 는 제외.
        registry.addInterceptor(featureGateInterceptor)
            .addPathPatterns("/api/v1/toss/**", "/api/v1/users/me/toss-credential");
    }
}
