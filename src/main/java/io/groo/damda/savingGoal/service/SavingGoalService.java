package io.groo.damda.savingGoal.service;

import io.groo.damda.savingGoal.service.dto.SavingGoalServiceDto;

import java.util.List;

public interface SavingGoalService {
    SavingGoalServiceDto.GoalInfo createSavingGoal(SavingGoalServiceDto.CreateCommand command);
    List<SavingGoalServiceDto.GoalInfo> getSavingGoals(Long userRowId);
    SavingGoalServiceDto.GoalInfo getSavingGoal(Long savingGoalId, Long userRowId);
    SavingGoalServiceDto.GoalInfo updateSavingGoal(Long savingGoalId, Long userRowId,
                                                    SavingGoalServiceDto.UpdateCommand command);
    SavingGoalServiceDto.GoalInfo contribute(Long savingGoalId, Long userRowId,
                                              SavingGoalServiceDto.ContributeCommand command);
    void deleteSavingGoal(Long savingGoalId, Long userRowId);
    void reorderSavingGoals(Long userRowId, List<SavingGoalServiceDto.ReorderItem> items);
}
