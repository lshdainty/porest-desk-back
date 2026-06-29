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
    /** SSO RS256 토큰 검증용 JWKS URI ({ssoBaseUrl}/.well-known/jwks.json). */
    private String ssoJwksUri;
}
