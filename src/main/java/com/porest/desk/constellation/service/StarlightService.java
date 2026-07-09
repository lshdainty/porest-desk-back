package com.porest.desk.constellation.service;

import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.todo.domain.Todo;

import java.time.LocalDate;

/**
 * 별빛 적립 엔진 — 할일/메모 이벤트의 부수효과로 호출된다 (별도 API 없음).
 * 규칙: 우선순위 가중(HIGH 3 · MEDIUM 2 · LOW 1), 메모 +1(일 최대 2),
 * 출처당 평생 1회(재완료 재적립 없음), 당일 해제만 회수, 수집 스냅샷 불변.
 */
public interface StarlightService {
    /** 할일 상태 토글 직후 호출 — COMPLETED 전이면 적립, 해제면 회수. TASK 타입만 대상. */
    void onTodoStatusToggled(Todo todo);

    /** 메모 생성 직후 호출 — +1 별빛 (일 한도 2 초과 시 무시). */
    void onMemoCreated(Memo memo);

    /** 메모 삭제 직후 호출 — 당일 적립분이면 회수. */
    void onMemoDeleted(Memo memo);

    /**
     * 스트릭 보호(구름 가림) 정산 — 마지막 수집일 이후 공백을 보호권으로 가릴 수 있으면
     * 소비해 스트릭을 잇는다(부족하면 소비하지 않고 스트릭 리셋). 새 날 첫 적립·오늘 현황 조회 시 호출.
     */
    void reconcileGuards(Long userRowId);

    /** 해당 날짜의 목표 별자리 — active 별자리를 날짜 기반(epochDay)으로 결정적 순환. */
    Constellation dailyTarget(LocalDate date);
}
