package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.RecurringTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository {
    Optional<RecurringTransaction> findById(Long rowId);
    List<RecurringTransaction> findByUser(Long userRowId);
    List<RecurringTransaction> findDueTransactions(LocalDate date);
    /** 해당 카테고리를 참조하는 (삭제되지 않은) 반복 거래 존재 여부 — 상위 승격 가드용. */
    boolean existsByCategory(Long categoryRowId);
    RecurringTransaction save(RecurringTransaction recurring);
    void delete(RecurringTransaction recurring);
}
