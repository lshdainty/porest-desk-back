package com.porest.desk.toss.credential.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 토스증권 API 인증정보. client_id/secret 은 AES-GCM 암호문으로만 보관한다(평문 미저장·미노출).
 * 사용자당 1건 — 재등록 시 기존 행을 갱신(undelete)한다.
 */
@Entity
@Table(name = "user_toss_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTossCredential extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    @Column(name = "client_id_enc", nullable = false, length = 512)
    private String clientIdEnc;

    @Column(name = "client_secret_enc", nullable = false, length = 1024)
    private String clientSecretEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_verified", nullable = false, length = 1)
    private YNType isVerified;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    private UserTossCredential(Long userRowId, String clientIdEnc, String clientSecretEnc, LocalDateTime verifiedAt) {
        this.userRowId = userRowId;
        this.clientIdEnc = clientIdEnc;
        this.clientSecretEnc = clientSecretEnc;
        this.isVerified = YNType.Y;
        this.verifiedAt = verifiedAt;
        this.isDeleted = YNType.N;
    }

    /** 검증 성공한 신규 크리덴셜. */
    public static UserTossCredential verified(Long userRowId, String clientIdEnc, String clientSecretEnc, LocalDateTime now) {
        return new UserTossCredential(userRowId, clientIdEnc, clientSecretEnc, now);
    }

    /** 재등록 — 암호문/검증시각 갱신 + undelete. */
    public void reRegister(String clientIdEnc, String clientSecretEnc, LocalDateTime now) {
        this.clientIdEnc = clientIdEnc;
        this.clientSecretEnc = clientSecretEnc;
        this.isVerified = YNType.Y;
        this.verifiedAt = now;
        this.isDeleted = YNType.N;
    }

    /** 해제 — 소프트 삭제. */
    public void disconnect() {
        this.isDeleted = YNType.Y;
        this.isVerified = YNType.N;
    }

    public void markUsed(LocalDateTime now) {
        this.lastUsedAt = now;
    }

    public boolean isActive() {
        return isDeleted == YNType.N && isVerified == YNType.Y;
    }
}
