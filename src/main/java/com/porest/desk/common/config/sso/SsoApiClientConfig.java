package com.porest.desk.common.config.sso;

import com.porest.desk.common.config.properties.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * SSO API 클라이언트 설정<br>
 * SSO 서비스와의 HTTP 통신을 위한 RestTemplate 설정
 */
@Configuration
public class SsoApiClientConfig {

    @Bean
    @Qualifier("ssoRestTemplate")
    public RestTemplate ssoRestTemplate(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setUriTemplateHandler(
                new DefaultUriBuilderFactory(appProperties.getSso().getBaseUrl()));

        return restTemplate;
    }
}
