package com.porest.desk.calendar.domain;

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
@Table(name = "event_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLabel extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "label_name", nullable = false, length = 50)
    private String labelName;

    @Column(name = "color", nullable = false, length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static EventLabel createLabel(User user, String labelName, String color, Integer sortOrder) {
        EventLabel label = new EventLabel();
        label.user = user;
        label.labelName = labelName;
        label.color = color;
        label.sortOrder = sortOrder != null ? sortOrder : 0;
        label.isDeleted = YNType.N;
        return label;
    }

    public void updateLabel(String labelName, String color) {
        this.labelName = labelName;
        this.color = color;
    }

    public void deleteLabel() {
        this.isDeleted = YNType.Y;
    }
}
