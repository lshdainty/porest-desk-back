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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 증권 관심목록 종목. (group, stock_master) 유니크 —
 * 같은 종목 재추가는 삭제 행을 되살린다({@link #restore}).
 */
@Entity
@Table(name = "stock_watch_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockWatchItem extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "group_row_id", nullable = false)
    private Long groupRowId;

    @Column(name = "stock_master_row_id", nullable = false)
    private Long stockMasterRowId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static StockWatchItem create(Long groupRowId, Long stockMasterRowId, int sortOrder) {
        StockWatchItem item = new StockWatchItem();
        item.groupRowId = groupRowId;
        item.stockMasterRowId = stockMasterRowId;
        item.sortOrder = sortOrder;
        item.isDeleted = YNType.N;
        return item;
    }

    public void delete() {
        this.isDeleted = YNType.Y;
    }

    /** 재추가 — (group, stock_master) 유니크 제약 때문에 새 행 대신 삭제 행을 되살린다. */
    public void restore(int sortOrder) {
        this.isDeleted = YNType.N;
        this.sortOrder = sortOrder;
    }

    public boolean isDeleted() {
        return YNType.Y == this.isDeleted;
    }
}
