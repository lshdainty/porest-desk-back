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

/**
 * 메모 태그 마스터 — {@code memo.tag} 자유 문자열의 주인이 되는 행.
 *
 * <p>{@link com.porest.desk.todo.domain.TodoTag} 를 그대로 미러링한다(활성 이름 UNIQUE ·
 * 생성 컬럼 · 색 · 삭제 플래그). <b>다른 점은 매핑 테이블이 없다는 것 하나다</b> —
 * 메모는 태그가 하나라 {@code memo.memo_tag_row_id} FK 한 컬럼으로 잇는다(사용자 결정 2026-09-08).
 * 할 일은 진짜 다대다여서 거기만 매핑 테이블이다.
 *
 * <p>이름 유일성은 <b>살아 있는 행 사이에서만</b> 건다 — DB 쪽은 생성 컬럼
 * {@code active_tag_name}(삭제 행은 NULL)을 낀 {@code UNIQUE(user_row_id, active_tag_name)} 이다.
 * 지운 이름은 다시 쓸 수 있어야 한다.
 */
@Entity
@Table(name = "memo_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoTag extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id", nullable = false)
    private User user;

    @Column(name = "tag_name", nullable = false, length = 50)
    private String tagName;

    @Column(name = "color", length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static MemoTag createTag(User user, String tagName, String color) {
        MemoTag tag = new MemoTag();
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
