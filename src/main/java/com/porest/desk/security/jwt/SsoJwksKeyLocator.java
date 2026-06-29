package com.porest.desk.security.jwt;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.porest.desk.common.config.properties.JwtProperties;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Locator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.Key;
import java.util.List;

/**
 * SSO RS256 토큰 검증용 공개키 Locator.
 *
 * <p>SSO 가 RS256(private) 로 서명한 토큰을 검증하기 위해, SSO 의 {@code /.well-known/jwks.json}
 * 에서 토큰 헤더의 {@code kid} 에 해당하는 RSA 공개키를 찾아 반환한다. nimbus {@code JWKSource}
 * 가 JWKS 를 캐시·갱신하므로 매 검증마다 네트워크 호출하지 않는다.
 *
 * <p>JWKSource 는 최초 검증 시 lazy 생성한다(컨텍스트 로딩 시점에 JWKS 미도달이어도 무방).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoJwksKeyLocator implements Locator<Key> {

    private final JwtProperties jwtProperties;
    private volatile JWKSource<SecurityContext> jwkSource;

    @Override
    public Key locate(Header header) {
        if (!(header instanceof JwsHeader jws)) {
            return null;
        }
        String kid = jws.getKeyId();
        try {
            JWKSelector selector = new JWKSelector(new JWKMatcher.Builder()
                    .keyType(KeyType.RSA)
                    .keyID(kid)
                    .build());
            List<JWK> jwks = source().get(selector, null);
            if (jwks.isEmpty()) {
                throw new IllegalStateException("SSO JWKS 에 일치하는 kid 없음: " + kid);
            }
            return ((RSAKey) jwks.get(0)).toRSAPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("SSO JWKS 공개키 조회 실패: " + e.getMessage(), e);
        }
    }

    private JWKSource<SecurityContext> source() throws Exception {
        JWKSource<SecurityContext> s = this.jwkSource;
        if (s == null) {
            synchronized (this) {
                s = this.jwkSource;
                if (s == null) {
                    s = JWKSourceBuilder.create(URI.create(jwtProperties.getSsoJwksUri()).toURL()).build();
                    this.jwkSource = s;
                }
            }
        }
        return s;
    }
}
