package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseCategory;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository {
    Optional<ExpenseCategory> findById(Long rowId);
    List<ExpenseCategory> findAllByUser(Long userRowId);
    ExpenseCategory save(ExpenseCategory expenseCategory);
    void delete(ExpenseCategory expenseCategory);
    boolean hasChildren(Long categoryRowId);
}
