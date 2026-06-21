package com.porest.desk.toss.credential.service;

import java.time.LocalDateTime;

/**
 * 사용자별 토스 크리덴셜 등록/상태조회/해제. client_id/secret 은 등록 시 토스 토큰발급으로 즉시 검증 후
 * AES-GCM 암호화하여 저장한다. 평문/암호문은 어떤 응답에도 노출하지 않는다.
 */
public interface TossCredentialService {

    /** 등록(또는 재등록) — 토스 토큰발급 검증 성공 시 암호화 저장. 실패 시 {@code TOSS_CREDENTIAL_INVALID}. */
    void register(Long userRowId, String clientId, String clientSecret);

    /** 연결 상태(연결여부·검증여부·검증일시). secret 미반환. */
    CredentialStatus getStatus(Long userRowId);

    /** 연결 해제(소프트 삭제) + 토큰캐시 무효화. */
    void disconnect(Long userRowId);

    record CredentialStatus(boolean connected, boolean verified, LocalDateTime verifiedAt) {
        public static CredentialStatus notConnected() {
            return new CredentialStatus(false, false, null);
        }
    }
}
