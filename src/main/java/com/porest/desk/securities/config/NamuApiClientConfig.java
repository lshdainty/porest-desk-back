package com.porest.desk.securities.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * 나무증권 Open API 클라이언트 설정. 토스({@code tossRestTemplate}) 패턴을 그대로 따른다.
 */
@Configuration
public class NamuApiClientConfig {

    @Bean
    @Qualifier("namuRestTemplate")
    public RestTemplate namuRestTemplate(NamuProperties namuProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(namuProperties.getConnectTimeout());
        factory.setReadTimeout(namuProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        // baseUrl 이 비어 있어도 빈 생성 자체는 막지 않는다(미설정 시 호출 시점에 가드).
        if (namuProperties.isConfigured()) {
            restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(namuProperties.getBaseUrl()));
        }
        return restTemplate;
    }
}
