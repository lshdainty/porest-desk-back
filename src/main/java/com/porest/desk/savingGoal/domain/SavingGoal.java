package com.porest.desk.savingGoal.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "saving_goal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingGoal extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Column(name = "current_amount", nullable = false)
    private Long currentAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "deadline_date")
    private LocalDate deadlineDate;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "color", length = 20)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_asset_row_id")
    private Asset linkedAsset;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_achieved", nullable = false, length = 1)
    private YNType isAchieved;

    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static SavingGoal createSavingGoal(User user, String title, String description,
                                              Long targetAmount, String currency,
                                              LocalDate deadlineDate, String icon, String color,
                                              Asset linkedAsset, Integer sortOrder) {
        SavingGoal goal = new SavingGoal();
        goal.user = user;
        goal.title = title;
        goal.description = description;
        goal.targetAmount = targetAmount;
        goal.currentAmount = 0L;
        goal.currency = currency != null ? currency : "KRW";
        goal.deadlineDate = deadlineDate;
        goal.icon = icon;
        goal.color = color;
        goal.linkedAsset = linkedAsset;
        goal.sortOrder = sortOrder != null ? sortOrder : 0;
        goal.isAchieved = YNType.N;
        goal.achievedAt = null;
        goal.isDeleted = YNType.N;
        return goal;
    }

    public void updateSavingGoal(String title, String description, Long targetAmount,
                                 LocalDate deadlineDate, String icon, String color,
                                 Asset linkedAsset) {
        this.title = title;
        this.description = description;
        this.targetAmount = targetAmount;
        this.deadlineDate = deadlineDate;
        this.icon = icon;
        this.color = color;
        this.linkedAsset = linkedAsset;
        // 목표 금액 변경 후 상태 재계산
        recalculateAchievement();
    }

    /**
     * 적립/회수 반영. amount 는 증감액(음수 가능).
     * 0 미만으로 내려가지 않도록 하한 보정.
     */
    public void contribute(Long amount) {
        if (amount == null) {
            return;
        }
        long next = this.currentAmount + amount;
        if (next < 0L) {
            next = 0L;
        }
        this.currentAmount = next;
        recalculateAchievement();
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deleteSavingGoal() {
        this.isDeleted = YNType.Y;
    }

    private void recalculateAchievement() {
        boolean achieved = this.targetAmount != null
            && this.targetAmount > 0L
            && this.currentAmount >= this.targetAmount;
        if (achieved && this.isAchieved != YNType.Y) {
            this.isAchieved = YNType.Y;
            this.achievedAt = LocalDateTime.now();
        } else if (!achieved && this.isAchieved == YNType.Y) {
            this.isAchieved = YNType.N;
            this.achievedAt = null;
        }
    }
}
