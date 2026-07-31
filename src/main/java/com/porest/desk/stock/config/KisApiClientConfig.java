package com.porest.desk.stock.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * KIS 마스터파일 다운로드용 RestTemplate 설정.
 *
 * <p>응답이 zip 바이너리라 별도 컨버터 설정 없이 byte[] 로 받는다.
 */
@Configuration
public class KisApiClientConfig {

    @Bean
    @Qualifier("kisRestTemplate")
    public RestTemplate kisRestTemplate(KisProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return new RestTemplate(factory);
    }
}
