package com.porest.desk.toss.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * 토스증권 Open API 클라이언트 설정<br>
 * 토스증권 서버와의 HTTP 통신을 위한 RestTemplate 빈을 등록한다.
 * 기존 SSO 클라이언트({@code ssoRestTemplate}) 패턴을 그대로 따른다.
 */
@Configuration
public class TossApiClientConfig {

    @Bean
    @Qualifier("tossRestTemplate")
    public RestTemplate tossRestTemplate(TossProperties tossProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(tossProperties.getConnectTimeout());
        factory.setReadTimeout(tossProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        // baseUrl 이 비어 있어도 빈 생성 자체는 막지 않는다(미설정 시 호출 시점에 가드).
        if (tossProperties.getBaseUrl() != null && !tossProperties.getBaseUrl().isBlank()) {
            restTemplate.setUriTemplateHandler(
                    new DefaultUriBuilderFactory(tossProperties.getBaseUrl()));
        }
        return restTemplate;
    }
}
