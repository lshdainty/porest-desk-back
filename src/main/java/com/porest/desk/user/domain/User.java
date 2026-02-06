package com.porest.desk.user.domain;

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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "sso_user_row_id")
    private Long ssoUserRowId;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "user_name", nullable = false, length = 20)
    private String userName;

    @Column(name = "user_email", nullable = false, length = 100)
    private String userEmail;

    @Column(name = "dashboard", columnDefinition = "TEXT")
    private String dashboard;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static User createUser(String userId, String userName, String userEmail) {
        User user = new User();
        user.userId = userId;
        user.userName = userName;
        user.userEmail = userEmail;
        user.timezone = "Asia/Seoul";
        user.isDeleted = YNType.N;
        return user;
    }

    public void updateFromSso(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public void updateDashboard(String dashboard) {
        this.dashboard = dashboard;
    }

    public void updateTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void deleteUser() {
        this.isDeleted = YNType.Y;
    }
}
