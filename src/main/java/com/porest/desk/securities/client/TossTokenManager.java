package com.porest.desk.securities.client;

import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.securities.client.dto.OAuth2TokenResponse;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 토스증권 인증. 표준 OAuth2 client_credentials 를 <b>폼 바디</b>로 보낸다.
 *
 * <p>인증 헤더는 {@code Bearer} 하나면 끝나므로 {@code applyAuth} 를 재정의하지 않는다.
 */
@Component
public class TossTokenManager extends AbstractBrokerTokenManager {

    private final RestTemplate tossRestTemplate;

    public TossTokenManager(UserSecuritiesCredentialRepository credentialRepository,
                            AesGcmCipher cipher,
                            BrokerTokenStore tokenStore,
                            @Qualifier("tossRestTemplate") RestTemplate tossRestTemplate) {
        super(credentialRepository, cipher, tokenStore);
        this.tossRestTemplate = tossRestTemplate;
    }

    @Override
    public SecuritiesBroker broker() {
        return SecuritiesBroker.TOSS;
    }

    @Override
    protected BrokerToken issueToken(String apiKey, String apiSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", apiKey);
        form.add("client_secret", apiSecret);

        OAuth2TokenResponse res = tossRestTemplate.postForObject(
            "/oauth2/token", new HttpEntity<>(form, headers), OAuth2TokenResponse.class);
        return res == null ? null : new BrokerToken(res.accessToken(), res.expiresIn());
    }
}
