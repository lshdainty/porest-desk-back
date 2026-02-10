package com.porest.desk.todo.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
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
@Table(name = "todo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_row_id")
    private TodoProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_row_id")
    private Todo parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_row_id")
    private UserGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_row_id")
    private User assignee;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TodoPriority priority;

    @Column(name = "category", length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TodoStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Todo createTodo(User user, String title, String content, TodoPriority priority, String category, LocalDate dueDate, TodoProject project, Todo parent) {
        Todo todo = new Todo();
        todo.user = user;
        todo.title = title;
        todo.content = content;
        todo.priority = priority;
        todo.category = category;
        todo.status = TodoStatus.PENDING;
        todo.dueDate = dueDate;
        todo.project = project;
        todo.parent = parent;
        todo.sortOrder = 0;
        todo.isDeleted = YNType.N;
        return todo;
    }

    public void updateTodo(String title, String content, TodoPriority priority, String category, LocalDate dueDate, TodoProject project) {
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.category = category;
        this.dueDate = dueDate;
        this.project = project;
    }

    public void toggleStatus() {
        if (this.status == TodoStatus.COMPLETED) {
            this.status = TodoStatus.PENDING;
            this.completedAt = null;
        } else {
            this.status = TodoStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        }
    }

    public void deleteTodo() {
        this.isDeleted = YNType.Y;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setGroup(UserGroup group) {
        this.group = group;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }
}
