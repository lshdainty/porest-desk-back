package io.groo.damda.namu.service;

import io.groo.damda.namu.dto.NamuAccountDetailDto;

import java.time.LocalDate;
import java.util.List;

/**
 * 나무증권 "계좌 자세히" 조회 (13b-0 · N5-나) — 계좌 자산 · 실현손익 · 권리(배당).
 *
 * <p>캐시가 없다 — 화면이 열 때만 부르고(본인 키 호출을 줄이는 것이 이 화면을 따로 둔 까닭),
 * 계좌는 지금처럼 첫 사용 가능 계좌({@code resolveAccountNo(userRowId, null)})다.
 */
public interface NamuAccountDetailQueryService {

    /** 국내 자산 현황(원화). 계좌가 없거나 응답이 비면 모든 칸 null. */
    NamuAccountDetailDto.AssetStatus getAssetStatus(Long userRowId);

    /**
     * 기간 실현손익 — 국내 {@code tradingPnl} 과 미국 {@code periodPnl}(원화 기준)을 <b>직렬로</b> 부른다.
     * 한쪽 실패는 그쪽만 null 로 두고 다른 쪽은 남긴다. 판 것만 싣는다.
     */
    NamuAccountDetailDto.RealizedPnl getRealizedPnl(Long userRowId, LocalDate from, LocalDate to);

    /**
     * 권리(배당). {@code kind} {@code scheduled} 는 예정, {@code held} 는 지난 것({@code from} 부터).
     * 지난 것은 화면이 "지난 것" 을 고를 때만 부른다.
     */
    NamuAccountDetailDto.Rights getRights(Long userRowId, String kind, LocalDate from);
}
