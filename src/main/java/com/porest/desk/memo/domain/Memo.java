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
@Table(name = "memo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memo extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_row_id")
    private MemoFolder folder;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_pinned", nullable = false, length = 1)
    private YNType isPinned;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Memo createMemo(User user, MemoFolder folder, String title, String content) {
        Memo memo = new Memo();
        memo.user = user;
        memo.folder = folder;
        memo.title = title;
        memo.content = content;
        memo.isPinned = YNType.N;
        memo.isDeleted = YNType.N;
        return memo;
    }

    public void updateMemo(MemoFolder folder, String title, String content) {
        this.folder = folder;
        this.title = title;
        this.content = content;
    }

    public void togglePin() {
        if (this.isPinned == YNType.Y) {
            this.isPinned = YNType.N;
        } else {
            this.isPinned = YNType.Y;
        }
    }

    public void deleteMemo() {
        this.isDeleted = YNType.Y;
    }
}
