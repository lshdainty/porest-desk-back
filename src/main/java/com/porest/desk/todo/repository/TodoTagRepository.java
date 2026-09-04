package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTag;

import java.util.List;
import java.util.Optional;

public interface TodoTagRepository {
    Optional<TodoTag> findById(Long rowId);
    List<TodoTag> findAllByUser(Long userRowId);
    boolean existsActiveByUserAndName(Long userRowId, String tagName, Long excludeRowId);
    List<TodoTag> findAllByIds(List<Long> ids);
    TodoTag save(TodoTag tag);
    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영 — EventLabelRepository.flush() 와 같은 이유. */
    void flush();
    void delete(TodoTag tag);
}
