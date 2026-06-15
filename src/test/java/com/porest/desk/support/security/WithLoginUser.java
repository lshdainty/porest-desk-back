package com.porest.desk.support.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API(@WebMvcTest) 테스트용 로그인 사용자 주입.
 *
 * <p>{@code @LoginUser} ArgumentResolver 는 SecurityContext 의 principal 이
 * {@link com.porest.desk.security.principal.JwtUserPrincipal} 일 때만 {@code UserPrincipal} 을
 * 주입한다. 따라서 spring-security-test 의 {@code @WithMockUser}(String principal)로는 동작하지
 * 않으므로, JwtUserPrincipal 을 직접 SecurityContext 에 넣는 전용 애노테이션을 사용한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithLoginUserSecurityContextFactory.class)
public @interface WithLoginUser {
    long rowId() default 1L;

    String userId() default "test-user";

    String userName() default "테스트";

    String userEmail() default "test@porest.com";
}
