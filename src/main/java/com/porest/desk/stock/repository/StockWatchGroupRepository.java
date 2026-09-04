package com.porest.desk.stock.repository;

import com.porest.desk.stock.domain.StockWatchGroup;

import java.util.List;
import java.util.Optional;

public interface StockWatchGroupRepository {
    List<StockWatchGroup> findAllActiveByUser(Long userRowId);

    Optional<StockWatchGroup> findActiveByIdAndUser(Long groupRowId, Long userRowId);

    long countActiveByUser(Long userRowId);

    /**
     * 활성(미삭제) 그룹 중 같은 이름이 있는지. {@code excludeRowId} 는 개명 시 자기 자신을 뺀다.
     *
     * <p>종전 시그니처에는 {@code excludeRowId} 가 없어 개명 검사가 자기 자신을 찾았고,
     * 서비스가 그걸 {@code !group.getGroupName().equals(name)} 이라는 <b>자바 비교</b>로 우회했다.
     * 자바 {@code equals} 는 대소문자를 구분하는데 DB 콜레이션({@code utf8mb4_unicode_ci})은
     * 무시한다 — 그래서 {@code "tech"} → {@code "TECH"} 개명이 "달라졌다" 로 통과한 뒤
     * DB 검사에서 자기 자신에 걸려 409 가 났다. 제외 조건을 DB 로 내리면 두 판정이 한 곳에서 난다.
     */
    boolean existsActiveByUserAndName(Long userRowId, String groupName, Long excludeRowId);

    StockWatchGroup save(StockWatchGroup group);

    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영. */
    void flush();
}
