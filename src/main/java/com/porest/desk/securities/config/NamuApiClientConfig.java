package com.porest.desk.securities.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * 나무증권 Open API 클라이언트 설정. 토스({@code tossRestTemplate}) 패턴을 그대로 따르되
 * <b>템플릿이 둘</b>이다.
 *
 * <ul>
 *   <li>{@code namuRestTemplate} — 조회. 환경에 따라 운영·모의투자 도메인이 갈린다.</li>
 *   <li>{@code namuAuthRestTemplate} — 토큰 발급. <b>항상 운영</b>이다(모의투자 미제공).</li>
 * </ul>
 *
 * <p>하나로 합치면 환경을 모의로 돌리는 순간 토큰 발급까지 moapi 로 나가 인증부터 죽는다.
 */
@Configuration
public class NamuApiClientConfig {

    @Bean
    @Qualifier("namuRestTemplate")
    public RestTemplate namuRestTemplate(NamuProperties namuProperties) {
        return build(namuProperties, namuProperties.getBaseUrl());
    }

    @Bean
    @Qualifier("namuAuthRestTemplate")
    public RestTemplate namuAuthRestTemplate(NamuProperties namuProperties) {
        return build(namuProperties, namuProperties.getAuthBaseUrl());
    }

    private static RestTemplate build(NamuProperties namuProperties, String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(namuProperties.getConnectTimeout());
        factory.setReadTimeout(namuProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        // baseUrl 이 비어 있어도 빈 생성 자체는 막지 않는다(미설정 시 호출 시점에 가드).
        if (baseUrl != null && !baseUrl.isBlank()) {
            restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(baseUrl));
        }
        return restTemplate;
    }
}
