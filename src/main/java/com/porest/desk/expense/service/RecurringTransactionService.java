package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;

import java.util.List;

public interface RecurringTransactionService {
    RecurringTransactionServiceDto.RecurringInfo createRecurring(RecurringTransactionServiceDto.CreateCommand command);
    List<RecurringTransactionServiceDto.RecurringInfo> getRecurrings(Long userRowId);
    RecurringTransactionServiceDto.RecurringInfo updateRecurring(Long recurringId, RecurringTransactionServiceDto.UpdateCommand command);
    void deleteRecurring(Long recurringId);
    RecurringTransactionServiceDto.RecurringInfo toggleActive(Long recurringId);
    void executeDueTransactions();
}
