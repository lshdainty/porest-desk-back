package io.groo.damda.expense.service;

import io.groo.damda.expense.service.dto.ExpenseSplitServiceDto;

import java.util.List;

public interface ExpenseSplitService {
    List<ExpenseSplitServiceDto.SplitInfo> getSplits(Long expenseRowId, Long userRowId);
    List<ExpenseSplitServiceDto.SplitInfo> replaceSplits(ExpenseSplitServiceDto.ReplaceCommand command);
    void deleteAllSplits(Long expenseRowId, Long userRowId);
}
