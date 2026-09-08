package com.porest.desk.todo.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "todo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends AuditingFieldsWithIp {
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

    // 하위 할 일(부모-자식)은 개념째 걷었다(사용자 결정 2026-09-08) — 이 엔티티는 더는
    // parent_row_id 를 매핑하지 않는다. <b>컬럼은 DB 에 그대로 있다</b>(옛 값을 지우지 않는다).
    // 매핑을 남겨 두면 아무도 안 읽는 연관이 남아 "언젠가 쓰겠지" 로 다시 자란다.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_row_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TodoType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TodoPriority priority;

    @Column(name = "category", length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TodoStatus status;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_pinned", nullable = false, length = 1)
    private YNType isPinned;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Todo createTodo(User user, String title, String content, TodoPriority priority, String category, LocalDate dueDate, TodoType type) {
        Todo todo = new Todo();
        todo.user = user;
        todo.type = type != null ? type : TodoType.TASK;
        todo.title = title;
        todo.content = content;
        todo.priority = priority;
        todo.category = category;
        todo.status = TodoStatus.PENDING;
        todo.dueDate = dueDate;
        todo.sortOrder = 0;
        todo.isPinned = YNType.N;
        todo.isDeleted = YNType.N;
        return todo;
    }

    /**
     * 수정. <b>NOT NULL 두 칸({@code title}·{@code priority})은 값이 오지 않으면 기존 값을 지킨다</b> —
     * 종전엔 null 을 그대로 덮어써 저장이 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다(QA #81).
     * 널 허용 칸({@code content}·{@code category}·{@code dueDate})은 반대로 그대로 덮는다 —
     * 거기서 null 은 "지운다" 는 뜻이고, 마감일을 지울 방법을 없애면 안 된다.
     */
    public void updateTodo(String title, String content, TodoPriority priority, String category, LocalDate dueDate) {
        if (title != null) this.title = title;
        if (priority != null) this.priority = priority;
        this.content = content;
        this.category = category;
        this.dueDate = dueDate;
    }

    /**
     * 상태를 <b>지정한 값</b>으로 바꾼다.
     *
     * <p>같은 상태가 다시 오면 아무것도 하지 않는다 — 재시도·중복 탭으로 "완료" 가 두 번 오면
     * {@code completedAt} 이 뒤로 밀려 "언제 끝냈는지" 가 요청할 때마다 달라진다(토글은 사이에
     * 대기를 거치므로 그때 다시 찍히는 게 맞다). 완료가 아닌 상태로 가면 완료 시각은 지운다
     * ({@code IN_PROGRESS} 포함) — 끝내지 않은 일에 끝낸 시각이 남아 있으면 안 된다.
     */
    public void changeStatus(TodoStatus next) {
        if (next == null || next == this.status) return;
        this.status = next;
        this.completedAt = next == TodoStatus.COMPLETED ? LocalDateTime.now() : null;
    }

    /** 본문 없는 옛 요청의 뜻 — 완료면 대기로, 그 밖이면 완료로. */
    public void toggleStatus() {
        changeStatus(this.status == TodoStatus.COMPLETED ? TodoStatus.PENDING : TodoStatus.COMPLETED);
    }

    public void togglePin() {
        if (this.isPinned == YNType.Y) {
            this.isPinned = YNType.N;
        } else {
            this.isPinned = YNType.Y;
        }
    }

    public void deleteTodo() {
        this.isDeleted = YNType.Y;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }
}
