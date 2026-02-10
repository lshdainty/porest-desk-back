package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.RecurringTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository {
    Optional<RecurringTransaction> findById(Long rowId);
    List<RecurringTransaction> findByUser(Long userRowId);
    List<RecurringTransaction> findDueTransactions(LocalDate date);
    RecurringTransaction save(RecurringTransaction recurring);
    void delete(RecurringTransaction recurring);
}
