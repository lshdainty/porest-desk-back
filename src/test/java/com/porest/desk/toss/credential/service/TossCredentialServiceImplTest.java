package com.porest.desk.toss.credential.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.toss.client.PerUserTossTokenManager;
import com.porest.desk.toss.client.TossTokenIssuer;
import com.porest.desk.toss.client.dto.TossTokenResponse;
import com.porest.desk.toss.credential.domain.UserTossCredential;
import com.porest.desk.toss.credential.repository.UserTossCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 토스 크리덴셜 등록/해제 프로세스 — 등록 시 토큰발급으로 검증 후 암호화 저장, 실패 시 거부.
 */
@ExtendWith(MockitoExtension.class)
class TossCredentialServiceImplTest {

    @Mock private UserTossCredentialRepository credentialRepository;
    @Mock private AesGcmCipher cipher;
    @Mock private TossTokenIssuer issuer;
    @Mock private PerUserTossTokenManager perUserTokenManager;
    @InjectMocks private TossCredentialServiceImpl sut;

    private static final long USER = 1L;

    @Test
    @DisplayName("유효 키 등록 시 토큰발급 검증 후 암호문으로 저장 + 토큰캐시 무효화")
    void register_success() {
        given(issuer.issue("id", "secret")).willReturn(new TossTokenResponse("tok", "Bearer", 3600L));
        given(cipher.encrypt("id")).willReturn("idEnc");
        given(cipher.encrypt("secret")).willReturn("secretEnc");
        given(credentialRepository.findByUserRowId(USER)).willReturn(Optional.empty());

        sut.register(USER, "id", "secret");

        verify(credentialRepository).save(any(UserTossCredential.class));
        verify(perUserTokenManager).invalidate(USER);
    }

    @Test
    @DisplayName("키 검증 실패(토큰발급 오류) 시 TOSS_CREDENTIAL_INVALID, 저장 안 함")
    void register_invalid() {
        given(issuer.issue("id", "bad")).willThrow(new RestClientException("401 Unauthorized"));

        assertThatThrownBy(() -> sut.register(USER, "id", "bad"))
            .isInstanceOf(ExternalServiceException.class);

        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("해제 시 소프트 삭제 + 토큰캐시 무효화")
    void disconnect() {
        UserTossCredential cred = UserTossCredential.verified(USER, "a", "b", LocalDateTime.now());
        given(credentialRepository.findByUserRowId(USER)).willReturn(Optional.of(cred));

        sut.disconnect(USER);

        assertThat(cred.getIsDeleted()).isEqualTo(YNType.Y);
        verify(perUserTokenManager).invalidate(USER);
    }
}
