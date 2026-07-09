package com.porest.desk.constellation.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 별자리 게이미피케이션 프로필 — 스트릭 보호권(구름 가림) 잔량/충전 진행도.
 * 보호권 규칙: 수집(GROWN) {@link #GUARD_CHARGE_THRESHOLD}일 누적마다 +1 충전, 최대 보유 {@link #GUARD_MAX}.
 */
@Entity
@Table(name = "constellation_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstellationProfile extends AuditingFieldsWithIp {
    public static final int GUARD_CHARGE_THRESHOLD = 7;
    public static final int GUARD_MAX = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "guard_count", nullable = false)
    private Integer guardCount;

    @Column(name = "grown_since_charge", nullable = false)
    private Integer grownSinceCharge;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static ConstellationProfile createProfile(User user) {
        ConstellationProfile profile = new ConstellationProfile();
        profile.user = user;
        profile.guardCount = 0;
        profile.grownSinceCharge = 0;
        profile.isDeleted = YNType.N;
        return profile;
    }

    /** 수집(GROWN) 1회 반영 — 7일 누적 시 보호권 +1(최대 2), 진행도 리셋. 캡 초과 충전분은 소멸. */
    public void recordGrown() {
        this.grownSinceCharge += 1;
        if (this.grownSinceCharge >= GUARD_CHARGE_THRESHOLD) {
            this.grownSinceCharge -= GUARD_CHARGE_THRESHOLD;
            if (this.guardCount < GUARD_MAX) {
                this.guardCount += 1;
            }
        }
    }

    /** 보호권 n개 소비 가능 여부. */
    public boolean canConsume(int n) {
        return this.guardCount >= n;
    }

    /** 보호권 n개 소비 — 부족하면 IllegalStateException (호출측에서 canConsume 선확인). */
    public void consumeGuards(int n) {
        if (!canConsume(n)) {
            throw new IllegalStateException("보호권 부족: 보유 " + this.guardCount + ", 요청 " + n);
        }
        this.guardCount -= n;
    }
}
