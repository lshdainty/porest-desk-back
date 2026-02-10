package com.porest.desk.todo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todo_tag_mapping", uniqueConstraints = {
    @UniqueConstraint(name = "UK_todo_tag", columnNames = {"todo_row_id", "tag_row_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoTagMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_row_id")
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_row_id")
    private TodoTag tag;

    public static TodoTagMapping create(Todo todo, TodoTag tag) {
        TodoTagMapping mapping = new TodoTagMapping();
        mapping.todo = todo;
        mapping.tag = tag;
        return mapping;
    }
}
