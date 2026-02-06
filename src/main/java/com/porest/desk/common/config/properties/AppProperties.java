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

    @Getter
    @Setter
    public static class Sso {
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins;
    }
}
