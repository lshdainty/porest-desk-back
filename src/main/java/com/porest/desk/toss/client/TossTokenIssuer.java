package com.porest.desk.toss.client;

import com.porest.desk.toss.client.dto.TossTokenResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 토스 OAuth2 Client Credentials 토큰을 임의 크리덴셜로 발급하는 공통 컴포넌트.
 * 사용자 개인 키({@link PerUserTossTokenManager})·크리덴셜 등록 검증(TossCredentialService)이
 * 모두 이 발급기를 공유한다.
 */
@Component
public class TossTokenIssuer {

    private final RestTemplate tossRestTemplate;

    public TossTokenIssuer(@Qualifier("tossRestTemplate") RestTemplate tossRestTemplate) {
        this.tossRestTemplate = tossRestTemplate;
    }

    /**
     * 주어진 client_id/secret 으로 access token 을 발급한다.
     * 실패 시 {@link org.springframework.web.client.RestClientException} 를 던진다(호출부 처리).
     */
    public TossTokenResponse issue(String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        return tossRestTemplate.postForObject("/oauth2/token", new HttpEntity<>(form, headers), TossTokenResponse.class);
    }
}
