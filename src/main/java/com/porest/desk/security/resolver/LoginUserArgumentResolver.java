package com.porest.desk.security.resolver;

import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.JwtUserPrincipal;
import com.porest.desk.security.principal.UserPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
            && parameter.getParameterType().equals(UserPrincipal.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserPrincipal principal) {
            return UserPrincipal.builder()
                .rowId(principal.getUserRowId())
                .userId(principal.getUserId())
                .userName(principal.getUserName())
                .userEmail(principal.getUserEmail())
                .build();
        }
        // TODO: SSO 인증 임시 비활성화 - 작업 완료 후 반드시 원복할 것 (return null로 변경)
        return UserPrincipal.builder()
            .rowId(1L)
            .userId("porestAdmin")
            .userName("관리자")
            .userEmail("hr.porest@gmail.com")
            .build();
    }
}
