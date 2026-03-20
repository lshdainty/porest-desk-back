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
}
