package com.porest.desk.apitoken.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 사용자가 자기 프로그램에서 desk 증권 조회 API 를 부를 때 쓰는 토큰.
 *
 * <p><b>토큰 하나 = 로그인 한 명.</b> 누가 이 토큰으로 부르든 주인의 증권사 키로 주인의 데이터만
 * 조회한다. desk 는 사용자마다 자기 키로 자기 데이터를 대신 불러 줄 뿐이고, 한 사람의 키가
 * 다른 사람을 위해 쓰이는 길은 없다.
 *
 * <p><b>원문은 저장하지 않는다</b> — 해시({@code token_hash})만 남긴다. 목록에서 알아보게 앞 몇
 * 글자({@code token_prefix})만 따로 둔다. 폐기는 소프트 삭제다.
 */
@Entity
@Table(name = "user_api_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserApiToken extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    /** 사용자가 붙인 이름 — 어느 프로그램에 넣은 토큰인지 알아보게. */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** SHA-256 16진수. 원문은 어디에도 없다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    private UserApiToken(Long userRowId, String name, String tokenHash, String tokenPrefix) {
        this.userRowId = userRowId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.isDeleted = YNType.N;
    }

    public static UserApiToken issue(Long userRowId, String name, String tokenHash, String tokenPrefix) {
        return new UserApiToken(userRowId, name, tokenHash, tokenPrefix);
    }

    public void revoke() {
        this.isDeleted = YNType.Y;
    }

    public boolean isActive() {
        return isDeleted == YNType.N;
    }

    /**
     * 마지막 사용 시각을 남긴다 — 단 {@code interval} 안에 이미 남겼으면 건너뛴다.
     *
     * <p>프로그램은 몇 초마다 부른다. 부를 때마다 UPDATE 를 내면 조회 API 가 쓰기 API 가 된다.
     * 화면이 보여 주는 건 "언제쯤 썼나" 라 분 단위면 충분하다.
     *
     * @return 이번에 남겼는지
     */
    public boolean touch(LocalDateTime now, Duration interval) {
        if (lastUsedAt != null && lastUsedAt.isAfter(now.minus(interval))) {
            return false;
        }
        this.lastUsedAt = now;
        return true;
    }
}
