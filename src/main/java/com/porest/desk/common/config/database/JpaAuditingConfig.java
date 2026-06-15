package com.porest.desk.common.config.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정.
 *
 * <p>{@code @EnableJpaAuditing} 을 앱 클래스(DeskApplication)가 아닌 전용 설정으로 분리한다.
 * 앱 클래스에 두면 {@code @WebMvcTest} 같은 JPA 없는 슬라이스 테스트가 "JPA metamodel must not be
 * empty" 로 깨지기 때문. auditor 는 {@link LoginUserAuditorAware} 가 타입으로 주입된다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
