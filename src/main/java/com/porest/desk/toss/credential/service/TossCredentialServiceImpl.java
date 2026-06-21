package com.porest.desk.toss.credential.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.toss.client.PerUserTossTokenManager;
import com.porest.desk.toss.client.TossTokenIssuer;
import com.porest.desk.toss.client.dto.TossTokenResponse;
import com.porest.desk.toss.credential.domain.UserTossCredential;
import com.porest.desk.toss.credential.repository.UserTossCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossCredentialServiceImpl implements TossCredentialService {

    private final UserTossCredentialRepository credentialRepository;
    private final AesGcmCipher cipher;
    private final TossTokenIssuer issuer;
    private final PerUserTossTokenManager perUserTokenManager;

    @Override
    @Transactional
    public void register(Long userRowId, String clientId, String clientSecret) {
        // 1) 토스 토큰발급으로 즉시 검증
        try {
            TossTokenResponse res = issuer.issue(clientId, clientSecret);
            if (res == null || res.accessToken() == null) {
                throw new ExternalServiceException(DeskErrorCode.TOSS_CREDENTIAL_INVALID);
            }
        } catch (RestClientException e) {
            log.warn("토스증권 크리덴셜 검증 실패 (userRowId={})", userRowId, e);
            throw new ExternalServiceException(DeskErrorCode.TOSS_CREDENTIAL_INVALID, e);
        }

        // 2) 암호화 저장(UPSERT)
        String idEnc = cipher.encrypt(clientId);
        String secretEnc = cipher.encrypt(clientSecret);
        LocalDateTime now = LocalDateTime.now();

        UserTossCredential cred = credentialRepository.findByUserRowId(userRowId).orElse(null);
        if (cred == null) {
            credentialRepository.save(UserTossCredential.verified(userRowId, idEnc, secretEnc, now));
        } else {
            cred.reRegister(idEnc, secretEnc, now);
        }
        // 3) 기존 토큰 캐시 무효화(다른 키였을 수 있음)
        perUserTokenManager.invalidate(userRowId);
        log.info("토스증권 크리덴셜 등록 완료 (userRowId={})", userRowId);
    }

    @Override
    @Transactional(readOnly = true)
    public CredentialStatus getStatus(Long userRowId) {
        return credentialRepository.findByUserRowId(userRowId)
            .filter(UserTossCredential::isActive)
            .map(c -> new CredentialStatus(true, true, c.getVerifiedAt()))
            .orElseGet(CredentialStatus::notConnected);
    }

    @Override
    @Transactional
    public void disconnect(Long userRowId) {
        credentialRepository.findByUserRowId(userRowId).ifPresent(UserTossCredential::disconnect);
        perUserTokenManager.invalidate(userRowId);
        log.info("토스증권 크리덴셜 해제 (userRowId={})", userRowId);
    }
}
