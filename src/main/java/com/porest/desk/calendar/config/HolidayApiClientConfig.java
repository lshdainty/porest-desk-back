package com.porest.desk.calendar.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 공휴일 외부 소스 호출용 RestTemplate 설정.
 *
 * <p>두 소스 모두 절대 URI 로 호출하므로 base URL 핸들러를 두지 않는다. 특일정보 API 의 인증키는
 * 이중 인코딩을 피하려고 클라이언트에서 직접 인코딩한 URI 를 넘긴다.
 */
@Configuration
public class HolidayApiClientConfig {

    @Bean
    @Qualifier("kasiRestTemplate")
    public RestTemplate kasiRestTemplate(HolidayProperties properties) {
        return build(properties.getKasi().getConnectTimeout(), properties.getKasi().getReadTimeout());
    }

    @Bean
    @Qualifier("holidaysKrRestTemplate")
    public RestTemplate holidaysKrRestTemplate(HolidayProperties properties) {
        return build(properties.getFallback().getConnectTimeout(), properties.getFallback().getReadTimeout());
    }

    private RestTemplate build(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
