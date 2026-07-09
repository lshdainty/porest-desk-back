package com.porest.desk.constellation.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.constellation.type.StarlightSourceType;
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
 * 별빛 적립 원장 — 할일 완료(HIGH 3 · MEDIUM 2 · LOW 1) / 메모 작성(+1, 일 최대 2).
 * UNIQUE(source_type, source_row_id): 같은 할일/메모는 평생 1회만 적립(완료 토글 반복 악용 차단).
 * 당일 회수는 soft delete — 행이 남아 unique 를 유지하므로 재완료해도 재적립되지 않는다.
 */
@Entity
@Table(name = "todo_starlight")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoStarlight extends AuditingFieldsWithIp {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private StarlightSourceType sourceType;

    @Column(name = "source_row_id", nullable = false)
    private Long sourceRowId;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "earn_date", nullable = false)
    private LocalDate earnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static TodoStarlight earn(User user, StarlightSourceType sourceType, Long sourceRowId,
                                     int points, LocalDate earnDate) {
        TodoStarlight starlight = new TodoStarlight();
        starlight.user = user;
        starlight.sourceType = sourceType;
        starlight.sourceRowId = sourceRowId;
        starlight.points = points;
        starlight.earnDate = earnDate;
        starlight.isDeleted = YNType.N;
        return starlight;
    }

    /** 회수 — soft delete (unique 행 유지 → 재적립 차단). */
    public void revoke() {
        this.isDeleted = YNType.Y;
    }
}
