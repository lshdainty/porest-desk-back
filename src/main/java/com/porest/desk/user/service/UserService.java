package com.porest.desk.user.service;

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

    /** 로그인 사용자 환경설정 조회 — 현재는 예산 알림 임계값만 */
    Integer getBudgetAlertThreshold(Long userRowId);

    /** 로그인 사용자 예산 알림 임계값 변경 */
    void updateBudgetAlertThreshold(Long userRowId, Integer threshold);
}
