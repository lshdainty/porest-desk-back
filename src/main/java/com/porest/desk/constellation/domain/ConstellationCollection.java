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

import java.time.LocalDate;

/**
 * 별자리 수집 스냅샷 (도감) — 일일 목표 도달 시점에 영구 기록.
 * 같은 별자리 반복 수집 가능(수집 횟수 = count). 이후 할일 완료 해제와 무관하게 불변.
 * UNIQUE(user, collected_date): 하루 1수집(일일 목표 1개).
 */
@Entity
@Table(name = "constellation_collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstellationCollection extends AuditingFieldsWithIp {
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constellation_row_id")
    private Constellation constellation;

    @Column(name = "collected_date", nullable = false)
    private LocalDate collectedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static ConstellationCollection collect(User user, Constellation constellation, LocalDate collectedDate) {
        ConstellationCollection collection = new ConstellationCollection();
        collection.user = user;
        collection.constellation = constellation;
        collection.collectedDate = collectedDate;
        collection.isDeleted = YNType.N;
        return collection;
    }
}
