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

    /**
     * 이 메모의 태그 마스터 행 — <b>메모는 태그가 하나다</b>(사용자 결정 2026-09-08).
     *
     * <p>할 일은 매핑 테이블({@code todo_tag_mapping})로 여러 태그를 다는데, 메모는 웹·앱 화면이
     * 하나만 고르게 되어 있어 FK 한 컬럼으로 잇는다. 매핑 테이블로 가면 "한 메모에 태그 하나" 를
     * DB 가 못 지키고, 지키려면 {@code UNIQUE(memo_row_id)} 를 걸어야 하는데 그건 FK 를 굳이
     * 다른 테이블에 옮겨 놓은 것과 같다.
     *
     * <p>{@link #tag} 는 이 행의 {@code tagName} 복사본이다 — 클라이언트가 보내고 받는 것은
     * 여전히 그 문자열이고(계약), 서버가 그 문자열로 마스터를 확보해 여기를 채운다.
     * 둘을 맞춰 두는 자리는 {@code MemoServiceImpl.applyTag} 하나다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_tag_row_id")
    private MemoTag memoTag;

    @Column(name = "color", length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_pinned", nullable = false, length = 1)
    private YNType isPinned;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Memo createMemo(User user, String title, String content,
                                  String tag, MemoTag memoTag, String color) {
        Memo memo = new Memo();
        memo.user = user;
        memo.title = title;
        memo.content = content;
        memo.tag = tag;
        memo.memoTag = memoTag;
        memo.color = color;
        memo.isPinned = YNType.N;
        memo.isDeleted = YNType.N;
        return memo;
    }

    /**
     * 수정 — 받은 값을 그대로 쓴다.
     *
     * <p><b>"안 보낸 칸은 유지" 판단은 서비스가 한다</b>(QA #96) — 여기 오는 값은 이미
     * 병합이 끝난 값이다. {@code title} 의 null 가드는 그대로 둔다: NOT NULL 컬럼이라
     * 어떤 경로로든 null 이 오면 저장이 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다(QA #81).
     * 나머지 칸은 널 허용이므로 그대로 덮는다(null = 지운다).
     *
     * <p>{@code tag} 문자열과 {@code memoTag} FK 는 <b>한 번에 함께</b> 덮는다 — 따로 두면
     * 한쪽만 바꾸는 경로가 생기고, 그 순간 이름과 마스터가 갈린다. 둘을 맞춰 넘기는 책임은
     * {@code MemoServiceImpl.applyTag} 에 있다.
     */
    public void updateMemo(String title, String content, String tag, MemoTag memoTag, String color) {
        if (title != null) this.title = title;
        this.content = content;
        this.tag = tag;
        this.memoTag = memoTag;
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
