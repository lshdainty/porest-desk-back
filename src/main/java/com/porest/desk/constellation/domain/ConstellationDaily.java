package com.porest.desk.constellation.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.constellation.type.DailyStatus;
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

import java.time.LocalDate;

/**
 * 사용자·일자별 관측 기록 (나의 밤하늘).
 * 행이 없는 날 = REST(쉼). 첫 별빛 적립 시 WITHERED 로 열리고, 목표 도달 시 GROWN 확정(이후 불변).
 * guard_used: 스트릭 보호(구름 가림)로 그 날의 공백이 가려졌는지.
 */
@Entity
@Table(name = "constellation_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstellationDaily extends AuditingFieldsWithIp {
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

    @Column(name = "obs_date", nullable = false)
    private LocalDate obsDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constellation_row_id")
    private Constellation constellation;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DailyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "guard_used", nullable = false, length = 1)
    private YNType guardUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    /** 첫 별빛 적립으로 그 날의 관측을 연다 (WITHERED 시작). */
    public static ConstellationDaily open(User user, LocalDate obsDate, Constellation constellation) {
        ConstellationDaily daily = new ConstellationDaily();
        daily.user = user;
        daily.obsDate = obsDate;
        daily.constellation = constellation;
        daily.points = 0;
        daily.status = DailyStatus.WITHERED;
        daily.guardUsed = YNType.N;
        daily.isDeleted = YNType.N;
        return daily;
    }

    /** 보호권 소비로 공백일을 가린 REST 행 (활동 없던 날의 브리지 기록). */
    public static ConstellationDaily restBridged(User user, LocalDate obsDate, Constellation constellation) {
        ConstellationDaily daily = open(user, obsDate, constellation);
        daily.status = DailyStatus.REST;
        daily.guardUsed = YNType.Y;
        return daily;
    }

    public void addPoints(int points) {
        this.points += points;
    }

    /** 회수 반영 — 0 밑으로 내려가지 않게 방어. GROWN 확정 상태/수집 스냅샷은 불변. */
    public void subtractPoints(int points) {
        this.points = Math.max(0, this.points - points);
    }

    /** 목표 도달 — 수집 확정 (이후 회수돼도 되돌리지 않음). */
    public void grow() {
        this.status = DailyStatus.GROWN;
    }

    /** 기존 행(WITHERED 등)에 보호 가림 마킹. */
    public void markGuardUsed() {
        this.guardUsed = YNType.Y;
    }

    public boolean isGrown() {
        return this.status == DailyStatus.GROWN;
    }
}
