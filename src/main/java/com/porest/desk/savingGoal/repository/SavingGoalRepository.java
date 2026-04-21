package com.porest.desk.savingGoal.repository;

import com.porest.desk.savingGoal.domain.SavingGoal;

import java.util.List;
import java.util.Optional;

public interface SavingGoalRepository {
    Optional<SavingGoal> findById(Long rowId);
    List<SavingGoal> findByUser(Long userRowId);
    SavingGoal save(SavingGoal savingGoal);
    void delete(SavingGoal savingGoal);
}
