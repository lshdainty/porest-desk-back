package com.porest.desk.stock.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 종목 마스터파일 다운로드용 RestTemplate 설정.
 *
 * <p>응답이 zip·고정폭 바이너리라 별도 컨버터 설정 없이 byte[] 로 받는다.
 * 소스별 base URL 은 요청마다 붙이므로 여기서는 타임아웃만 정한다.
 */
@Configuration
public class MasterFileClientConfig {

    @Bean
    @Qualifier("masterFileRestTemplate")
    public RestTemplate masterFileRestTemplate(MasterFileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return new RestTemplate(factory);
    }
}
