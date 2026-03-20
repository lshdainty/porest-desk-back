package com.porest.desk.memo.domain;

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
@Table(name = "memo_folder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoFolder extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_row_id")
    private MemoFolder parent;

    @Column(name = "folder_name", nullable = false, length = 100)
    private String folderName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static MemoFolder createFolder(User user, MemoFolder parent, String folderName) {
        MemoFolder folder = new MemoFolder();
        folder.user = user;
        folder.parent = parent;
        folder.folderName = folderName;
        folder.sortOrder = 0;
        folder.isDeleted = YNType.N;
        return folder;
    }

    public void updateFolder(MemoFolder parent, String folderName, Integer sortOrder) {
        this.parent = parent;
        this.folderName = folderName;
        this.sortOrder = sortOrder;
    }

    public void deleteFolder() {
        this.isDeleted = YNType.Y;
    }
}
