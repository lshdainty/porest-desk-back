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
import jakarta.persistence.Version;
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "tag", length = 50)
    private String tag;

    @Column(name = "color", length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_pinned", nullable = false, length = 1)
    private YNType isPinned;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Memo createMemo(User user, String title, String content,
                                  String tag, String color) {
        Memo memo = new Memo();
        memo.user = user;
        memo.title = title;
        memo.content = content;
        memo.tag = tag;
        memo.color = color;
        memo.isPinned = YNType.N;
        memo.isDeleted = YNType.N;
        return memo;
    }

    /**
     * 수정. <b>{@code title} 은 NOT NULL 이라 값이 오지 않으면 기존 제목을 지킨다</b> —
     * 종전엔 null 을 그대로 덮어써 저장이 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다(QA #81).
     * 나머지 칸은 널 허용이므로 그대로 덮는다(null = 지운다).
     */
    public void updateMemo(String title, String content, String tag, String color) {
        if (title != null) this.title = title;
        this.content = content;
        this.tag = tag;
        this.color = color;
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
