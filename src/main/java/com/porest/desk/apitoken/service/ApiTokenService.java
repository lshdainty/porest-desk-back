package com.porest.desk.apitoken.service;

import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.Caller;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.IssuedToken;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.TokenInfo;

import java.util.List;
import java.util.Optional;

/**
 * 프로그램용 API 토큰 — 발급·목록·폐기, 그리고 요청에 실려 온 토큰의 주인 확인.
 */
public interface ApiTokenService {

    /** 새 토큰. 원문은 반환값에만 있다. */
    IssuedToken issue(Long userRowId, String name);

    List<TokenInfo> getTokens(Long userRowId);

    void revoke(Long userRowId, Long tokenRowId);

    /** 이 사용자의 살아 있는 토큰 전부 — 이용 해지할 때 쓴다. @return 폐기한 개수 */
    int revokeAll(Long userRowId);

    /**
     * 토큰 원문으로 주인을 찾는다. 모양이 아니거나 · 없거나 · 폐기됐거나 · 주인이 해지했으면 빈 값.
     * 찾으면 마지막 사용 시각을 남긴다(분 단위로 묶어서).
     */
    Optional<Caller> authenticate(String rawToken);
}
