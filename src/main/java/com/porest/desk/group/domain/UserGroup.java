package com.porest.desk.group.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.group.type.GroupType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGroup extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    private GroupType groupType;

    @Column(name = "invite_code", length = 20, unique = true)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserGroupMember> members = new ArrayList<>();

    public static UserGroup createGroup(String groupName, String description, GroupType groupType) {
        UserGroup group = new UserGroup();
        group.groupName = groupName;
        group.description = description;
        group.groupType = groupType;
        group.inviteCode = generateInviteCode();
        group.isDeleted = YNType.N;
        return group;
    }

    public void updateGroup(String groupName, String description, GroupType groupType) {
        this.groupName = groupName;
        this.description = description;
        this.groupType = groupType;
    }

    public void deleteGroup() {
        this.isDeleted = YNType.Y;
    }

    public String regenerateInviteCode() {
        this.inviteCode = generateInviteCode();
        return this.inviteCode;
    }

    public void addMember(UserGroupMember member) {
        this.members.add(member);
    }

    private static String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
