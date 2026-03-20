package com.porest.desk.group.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "group_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupType extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "type_name", nullable = false, length = 50)
    private String typeName;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static GroupType createGroupType(User user, String typeName, String color, int sortOrder) {
        GroupType groupType = new GroupType();
        groupType.user = user;
        groupType.typeName = typeName;
        groupType.color = color;
        groupType.sortOrder = sortOrder;
        groupType.isDeleted = YNType.N;
        return groupType;
    }

    public void updateGroupType(String typeName, String color, int sortOrder) {
        this.typeName = typeName;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public void deleteGroupType() {
        this.isDeleted = YNType.Y;
    }
}
