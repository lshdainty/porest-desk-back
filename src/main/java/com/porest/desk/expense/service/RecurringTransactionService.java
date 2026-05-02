package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;

import java.util.List;

public interface RecurringTransactionService {
    RecurringTransactionServiceDto.RecurringInfo createRecurring(RecurringTransactionServiceDto.CreateCommand command);
    List<RecurringTransactionServiceDto.RecurringInfo> getRecurrings(Long userRowId);
    List<RecurringTransactionServiceDto.RecurringInfo> getRecurrings(Long userRowId, boolean upcomingOnly, Integer limit);
    RecurringTransactionServiceDto.RecurringInfo updateRecurring(Long recurringId, Long userRowId, RecurringTransactionServiceDto.UpdateCommand command);
    void deleteRecurring(Long recurringId, Long userRowId);
    RecurringTransactionServiceDto.RecurringInfo toggleActive(Long recurringId, Long userRowId);
    void executeDueTransactions();
}
