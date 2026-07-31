package com.porest.desk.stock.domain;

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

/**
 * 증권 관심목록 그룹 (사용자별, 예: "관심", "미국 기술주").
 *
 * <p>그룹명 중복 검사는 soft delete 와의 유니크 제약 충돌을 피해 서비스가 활성 행만 대상으로 한다.
 */
@Entity
@Table(name = "stock_watch_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockWatchGroup extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static StockWatchGroup create(Long userRowId, String groupName, int sortOrder) {
        StockWatchGroup group = new StockWatchGroup();
        group.userRowId = userRowId;
        group.groupName = groupName;
        group.sortOrder = sortOrder;
        group.isDeleted = YNType.N;
        return group;
    }

    public void rename(String groupName) {
        this.groupName = groupName;
    }

    public void delete() {
        this.isDeleted = YNType.Y;
    }

    public boolean isDeleted() {
        return YNType.Y == this.isDeleted;
    }

    public boolean isOwnedBy(Long userRowId) {
        return this.userRowId.equals(userRowId);
    }
}
