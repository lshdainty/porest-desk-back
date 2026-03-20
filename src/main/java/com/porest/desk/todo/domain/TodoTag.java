package com.porest.desk.todo.domain;

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
@Table(name = "todo_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoTag extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "tag_name", nullable = false, length = 50)
    private String tagName;

    @Column(name = "color", length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static TodoTag createTag(User user, String tagName, String color) {
        TodoTag tag = new TodoTag();
        tag.user = user;
        tag.tagName = tagName;
        tag.color = color;
        tag.isDeleted = YNType.N;
        return tag;
    }

    public void updateTag(String tagName, String color) {
        this.tagName = tagName;
        this.color = color;
    }

    public void deleteTag() {
        this.isDeleted = YNType.Y;
    }
}
