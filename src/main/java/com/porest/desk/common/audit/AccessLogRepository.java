package com.porest.desk.common.audit;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AccessLog Repository.
 *
 * <p>접속기록은 남기고 읽기만 한다. 수정·삭제 메소드를 두지 않는 것은 고시 제8조 2항
 * (위·변조 방지)에 따른 의도이며, DB 계정 권한으로도 함께 막는다.</p>
 */
public interface AccessLogRepository {

    /** 접속기록 저장. */
    void save(AccessLog accessLog);

    /**
     * 수행자별 접속기록 조회 (최신순).
     *
     * @param actorId 수행자 계정
     * @param limit 최대 건수
     */
    List<AccessLog> findByActor(String actorId, int limit);

    /**
     * 기간별 접속기록 조회 (최신순) — 정기 점검용.
     *
     * @param from 시작 일시 [UTC]
     * @param to 종료 일시 [UTC]
     * @param limit 최대 건수
     */
    List<AccessLog> findByPeriod(LocalDateTime from, LocalDateTime to, int limit);
}
