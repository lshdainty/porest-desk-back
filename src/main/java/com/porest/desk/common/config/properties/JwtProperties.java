package com.porest.desk.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpiration;
    /** (레거시) SSO 서비스 토큰 서명용 공유 secret — createServiceToken(desk→SSO 비번변경 프록시)에서만 사용. */
    private String ssoSecret;
    /** SSO RS256 토큰 검증용 JWKS URI ({ssoBaseUrl}/.well-known/jwks.json). */
    private String ssoJwksUri;
}
