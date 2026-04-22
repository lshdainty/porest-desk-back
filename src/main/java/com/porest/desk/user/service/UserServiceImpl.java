package com.porest.desk.user.service;

import com.porest.core.controller.ApiResponse;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String SSO_CHANGE_PASSWORD_PATH = "/api/v1/auth/password/change";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Qualifier("ssoRestTemplate")
    private final RestTemplate ssoRestTemplate;

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        // SSO 서비스 호출을 위한 단기 토큰 생성
        String serviceToken = jwtTokenProvider.createServiceToken(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceToken);

        Map<String, String> requestBody = Map.of(
                "currentPassword", currentPassword,
                "newPassword", newPassword,
                "confirmPassword", confirmPassword
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<ApiResponse<Void>> response = ssoRestTemplate.exchange(
                    SSO_CHANGE_PASSWORD_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ApiResponse<Void> body = response.getBody();
            if (body != null && !body.isSuccess()) {
                throw new InvalidValueException(DeskErrorCode.USER_PASSWORD_CHANGE_FAILED, body.getMessage());
            }

            log.info("Password changed successfully for user: {}", userId);

        } catch (HttpClientErrorException e) {
            log.warn("SSO password change client error for user {}: {}", userId, e.getMessage());
            // SSO 에러 메시지 추출
            String errorMessage = extractSsoErrorMessage(e);
            throw new InvalidValueException(DeskErrorCode.USER_PASSWORD_CHANGE_FAILED, errorMessage);
        } catch (RestClientException e) {
            log.error("SSO password change request failed for user {}: {}", userId, e.getMessage(), e);
            throw new ExternalServiceException(DeskErrorCode.SSO_SERVICE_ERROR, "SSO 비밀번호 변경 API 호출 실패", e);
        }
    }

    @Override
    public Integer getBudgetAlertThreshold(Long userRowId) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        Integer v = user.getBudgetAlertThreshold();
        return v != null ? v : 85;
    }

    @Override
    @Transactional
    public void updateBudgetAlertThreshold(Long userRowId, Integer threshold) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        user.updateBudgetAlertThreshold(threshold);
        log.info("예산 알림 임계값 변경: userRowId={}, threshold={}%", userRowId, user.getBudgetAlertThreshold());
    }

    private String extractSsoErrorMessage(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();
            // JSON에서 message 필드 추출 (간단한 파싱)
            if (responseBody.contains("\"message\"")) {
                int start = responseBody.indexOf("\"message\"") + 11;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    return responseBody.substring(start, end);
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract SSO error message", ex);
        }
        return "비밀번호 변경에 실패했습니다";
    }
}
