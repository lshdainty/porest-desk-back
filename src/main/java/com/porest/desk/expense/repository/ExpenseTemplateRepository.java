package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseTemplate;

import java.util.List;
import java.util.Optional;

public interface ExpenseTemplateRepository {
    Optional<ExpenseTemplate> findById(Long rowId);
    List<ExpenseTemplate> findByUser(Long userRowId);
    ExpenseTemplate save(ExpenseTemplate template);
    void delete(ExpenseTemplate template);

    /** 활성(미삭제) 프리셋 중 같은 이름이 있는지. {@code excludeRowId} 는 수정 시 자기 자신을 뺀다. */
    boolean existsActiveByUserAndName(Long userRowId, String templateName, Long excludeRowId);

    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영. */
    void flush();
}
