package com.porest.desk.user.service;

import com.porest.desk.user.controller.dto.UserApiDto.PreferencesResponse;
import com.porest.desk.user.controller.dto.UserApiDto.UpdatePreferencesReq;
import com.porest.desk.user.controller.dto.UserApiDto;

/**
 * 사용자 서비스 인터페이스
 */
public interface UserService {

    /**
     * 비밀번호 변경 (SSO 연동)
     *
     * @param userId          사용자 ID
     * @param currentPassword 현재 비밀번호
     * @param newPassword     새 비밀번호
     * @param confirmPassword 새 비밀번호 확인
     */
    void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword);

    /**
     * 비밀번호 검증 (SSO 연동) — 민감 작업 재인증용.
     * 일치하지 않으면 InvalidValueException 발생.
     */
    void verifyPassword(String userId, String password);

    /** 예산 알림 임계값 단건 조회 — 예산 경고 계산 등 내부 사용. */
    Integer getBudgetAlertThreshold(Long userRowId);

    /** 로그인 사용자 알림 환경설정 전체 조회. */
    PreferencesResponse getPreferences(Long userRowId);

    /** 로그인 사용자 알림 환경설정 부분 수정 (PATCH) 후 최신 상태 반환. */
    PreferencesResponse updatePreferences(Long userRowId, UpdatePreferencesReq request);

    /**
     * 금액 가리기 목록 조회.
     *
     * @return 저장된 적 없으면 {@code hideCards} 가 {@code null} — 빈 목록과 뜻이 다르다.
     *         클라이언트는 {@code null} 을 받으면 내려받지 말고 자기 로컬 값을 올려야 한다
     */
    UserApiDto.HideCardsResponse getHideCards(Long userRowId);

    /** 금액 가리기 목록 교체. 부분 갱신이 아니라 통째로 바꾼다. */
    UserApiDto.HideCardsResponse updateHideCards(Long userRowId, UserApiDto.UpdateHideCardsReq request);
}
