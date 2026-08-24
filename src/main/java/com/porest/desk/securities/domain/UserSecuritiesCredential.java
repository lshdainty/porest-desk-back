package com.porest.desk.securities.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.securities.type.SecuritiesBroker;
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
 * 사용자별 증권사 Open API 인증정보. 키/시크릿은 AES-GCM 암호문으로만 보관한다(평문 미저장·미노출).
 * 사용자 × 증권사 1건 — 재등록 시 기존 행을 갱신(undelete)한다.
 *
 * <p>컬럼 이름이 {@code api_key}/{@code api_secret} 인 이유 — 같은 자리를 토스는
 * {@code client_id}/{@code client_secret}, 나무는 {@code appkey}/{@code appsecretkey} 라 부른다.
 * 어느 한쪽 이름을 쓰면 반대쪽에서 거짓말이 된다.
 */
@Entity
@Table(name = "user_securities_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSecuritiesCredential extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "broker", nullable = false, length = 20)
    private SecuritiesBroker broker;

    @Column(name = "api_key_enc", nullable = false, length = 512)
    private String apiKeyEnc;

    @Column(name = "api_secret_enc", nullable = false, length = 1024)
    private String apiSecretEnc;

    /** 가계부 자산 평가에 쓸 시세 소스. 사용자당 최대 1건 Y — 서비스가 보장한다(DB 제약 없음). */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_primary", nullable = false, length = 1)
    private YNType isPrimary;

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

    private UserSecuritiesCredential(Long userRowId, SecuritiesBroker broker,
                                     String apiKeyEnc, String apiSecretEnc, LocalDateTime verifiedAt) {
        this.userRowId = userRowId;
        this.broker = broker;
        this.apiKeyEnc = apiKeyEnc;
        this.apiSecretEnc = apiSecretEnc;
        this.isPrimary = YNType.N;
        this.isVerified = YNType.Y;
        this.verifiedAt = verifiedAt;
        this.isDeleted = YNType.N;
    }

    /** 검증 성공한 신규 크리덴셜. */
    public static UserSecuritiesCredential verified(Long userRowId, SecuritiesBroker broker,
                                                    String apiKeyEnc, String apiSecretEnc, LocalDateTime now) {
        return new UserSecuritiesCredential(userRowId, broker, apiKeyEnc, apiSecretEnc, now);
    }

    /** 재등록 — 암호문/검증시각 갱신 + undelete. 기본 소스 지정은 건드리지 않는다. */
    public void reRegister(String apiKeyEnc, String apiSecretEnc, LocalDateTime now) {
        this.apiKeyEnc = apiKeyEnc;
        this.apiSecretEnc = apiSecretEnc;
        this.isVerified = YNType.Y;
        this.verifiedAt = now;
        this.isDeleted = YNType.N;
    }

    /** 해제 — 소프트 삭제. 기본 소스였다면 승계는 서비스가 정한다. */
    public void disconnect() {
        this.isDeleted = YNType.Y;
        this.isVerified = YNType.N;
        this.isPrimary = YNType.N;
    }

    public void markPrimary(boolean primary) {
        this.isPrimary = primary ? YNType.Y : YNType.N;
    }

    public void markUsed(LocalDateTime now) {
        this.lastUsedAt = now;
    }

    public boolean isActive() {
        return isDeleted == YNType.N && isVerified == YNType.Y;
    }

    public boolean isPrimarySource() {
        return isPrimary == YNType.Y;
    }
}
