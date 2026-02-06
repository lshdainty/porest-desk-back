package com.porest.desk.common.config.database;

import com.porest.core.security.AuditorPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LoginUserAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return Optional.of("system");
        }
        if (authentication.getPrincipal() instanceof AuditorPrincipal principal) {
            return Optional.of(principal.getAuditorId());
        }
        return Optional.of(authentication.getName());
    }
}
