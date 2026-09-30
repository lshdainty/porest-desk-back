package com.porest.desk.apitoken.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.apitoken.domain.UserApiToken;
import com.porest.desk.apitoken.repository.UserApiTokenRepository;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.Caller;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.IssuedToken;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.TokenInfo;
import com.porest.desk.apitoken.type.ApiTokenFormat;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTokenServiceImpl implements ApiTokenService {

    /** 사용자당 살아 있는 토큰 수. 프로그램마다 하나씩 쓰기에 충분하고, 잊힌 토큰이 쌓이지 않게 막는다. */
    static final int MAX_ACTIVE_TOKENS = 5;

    static final int NAME_MAX_LENGTH = 50;

    /** 마지막 사용 시각을 쓰는 최소 간격 — {@link UserApiToken#touch} 참고. */
    static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final UserApiTokenRepository apiTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssuedToken issue(Long userRowId, String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new InvalidValueException(DeskErrorCode.API_TOKEN_NAME_INVALID);
        }
        if (apiTokenRepository.countByUserRowIdAndIsDeleted(userRowId, YNType.N) >= MAX_ACTIVE_TOKENS) {
            throw new InvalidValueException(DeskErrorCode.API_TOKEN_LIMIT_EXCEEDED);
        }

        String token = ApiTokenFormat.generate(secureRandom);
        UserApiToken saved = apiTokenRepository.save(UserApiToken.issue(
            userRowId, trimmed, ApiTokenFormat.hash(token), ApiTokenFormat.displayPrefix(token)));

        log.info("API 토큰 발급. userRowId={}, tokenRowId={}", userRowId, saved.getRowId());
        return new IssuedToken(saved.getRowId(), saved.getName(), token, saved.getTokenPrefix(),
            saved.getCreateAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TokenInfo> getTokens(Long userRowId) {
        return apiTokenRepository.findAllByUserRowIdAndIsDeletedOrderByRowIdDesc(userRowId, YNType.N).stream()
            .map(TokenInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void revoke(Long userRowId, Long tokenRowId) {
        UserApiToken token = apiTokenRepository.findByRowIdAndUserRowIdAndIsDeleted(tokenRowId, userRowId, YNType.N)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.API_TOKEN_NOT_FOUND));
        token.revoke();
        log.info("API 토큰 폐기. userRowId={}, tokenRowId={}", userRowId, tokenRowId);
    }

    @Override
    @Transactional
    public int revokeAll(Long userRowId) {
        List<UserApiToken> active = apiTokenRepository.findAllByUserRowIdAndIsDeletedOrderByRowIdDesc(userRowId, YNType.N);
        active.forEach(UserApiToken::revoke);
        if (!active.isEmpty()) {
            log.info("API 토큰 일괄 폐기. userRowId={}, 개수={}", userRowId, active.size());
        }
        return active.size();
    }

    @Override
    @Transactional
    public Optional<Caller> authenticate(String rawToken) {
        if (!ApiTokenFormat.looksLike(rawToken)) {
            return Optional.empty();
        }
        UserApiToken token = apiTokenRepository.findByTokenHashAndIsDeleted(ApiTokenFormat.hash(rawToken), YNType.N)
            .orElse(null);
        if (token == null) {
            return Optional.empty();
        }

        // 해지·삭제한 사람의 토큰은 해지할 때 같이 폐기한다. 그래도 여기서 한 번 더 본다 —
        // 그 경로를 안 탄 삭제(관리자 삭제 등)가 있어도 토큰이 살아나지 않게.
        User user = userRepository.findById(token.getUserRowId()).orElse(null);
        if (user == null || user.isWithdrawn()) {
            log.warn("API 토큰 거절 - 주인이 없거나 해지했다. tokenRowId={}", token.getRowId());
            return Optional.empty();
        }

        token.touch(LocalDateTime.now(ZoneOffset.UTC), LAST_USED_WRITE_INTERVAL);
        return Optional.of(new Caller(token.getRowId(), user.getRowId(), user.getUserId(),
            user.getUserName(), user.getUserEmail()));
    }
}
