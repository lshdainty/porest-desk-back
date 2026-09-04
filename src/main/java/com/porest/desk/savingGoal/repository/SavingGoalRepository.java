package com.porest.desk.savingGoal.repository;

import com.porest.desk.savingGoal.domain.SavingGoal;

import java.util.List;
import java.util.Optional;

public interface SavingGoalRepository {
    Optional<SavingGoal> findById(Long rowId);
    List<SavingGoal> findByUser(Long userRowId);
    SavingGoal save(SavingGoal savingGoal);
    void delete(SavingGoal savingGoal);

    /**
     * 활성(미삭제) 저축 목표 중 같은 이름이 있는지. {@code excludeRowId} 는 수정 시 자기 자신을 뺀다
     * — 빼지 않으면 이름을 그대로 두고 금액만 고치는 저장이 영영 막힌다.
     */
    boolean existsActiveByUserAndTitle(Long userRowId, String title, Long excludeRowId);

    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영. */
    void flush();
}
