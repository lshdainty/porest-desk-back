package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseTemplate;

import java.util.List;
import java.util.Optional;

public interface ExpenseTemplateRepository {
    Optional<ExpenseTemplate> findById(Long rowId);
    List<ExpenseTemplate> findByUser(Long userRowId);
    ExpenseTemplate save(ExpenseTemplate template);
    void delete(ExpenseTemplate template);
}
