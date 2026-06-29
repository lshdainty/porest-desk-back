package com.porest.desk.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Sso sso = new Sso();
    private Cors cors = new Cors();
    private Security security = new Security();

    @Getter
    @Setter
    public static class Sso {
        private String baseUrl;
        /** client_credentials 그랜트용 desk 클라이언트 시크릿 (desk→SSO 서비스 토큰 발급). env 로만 주입. */
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class Security {
        /** 민감값 AES-256-GCM 암호화 키 (Base64 인코딩된 32바이트). env 로만 주입. */
        private String encryptionKey;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins;
    }
}
