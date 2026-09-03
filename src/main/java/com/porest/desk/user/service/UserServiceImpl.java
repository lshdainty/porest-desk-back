package com.porest.desk.user.service;

import com.porest.core.controller.ApiResponse;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.client.SsoOAuth2Client;
import com.porest.desk.user.controller.dto.UserApiDto.PreferencesResponse;
import com.porest.desk.user.controller.dto.UserApiDto;
import com.porest.desk.user.controller.dto.UserApiDto.UpdatePreferencesReq;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.time.DateTimeException;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String SSO_CHANGE_PASSWORD_PATH = "/api/v1/auth/password/change";
    private static final String SSO_VERIFY_PASSWORD_PATH = "/api/v1/auth/password/verify";

    private final SsoOAuth2Client ssoOAuth2Client;
    private final UserRepository userRepository;

    @Qualifier("ssoRestTemplate")
    private final RestTemplate ssoRestTemplate;

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        // SSO 호출용 서비스 토큰(RS256)을 client_credentials 그랜트로 발급
        String serviceToken = ssoOAuth2Client.issueServiceToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceToken);

        // 서비스 토큰에는 사용자 식별자가 없으므로 대상 userId 를 body 에 담아 전달(SSO 가 body userId 수용)
        Map<String, String> requestBody = Map.of(
                "userId", userId,
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
            throw new ExternalServiceException(DeskErrorCode.SSO_SERVICE_ERROR, e);
        }
    }

    @Override
    public void verifyPassword(String userId, String password) {
        String serviceToken = ssoOAuth2Client.issueServiceToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceToken);

        // 서비스 토큰에는 사용자 식별자가 없으므로 대상 userId 를 body 에 담아 전달
        Map<String, String> requestBody = Map.of("userId", userId, "password", password);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<ApiResponse<Void>> response = ssoRestTemplate.exchange(
                    SSO_VERIFY_PASSWORD_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ApiResponse<Void> body = response.getBody();
            if (body != null && !body.isSuccess()) {
                throw new InvalidValueException(DeskErrorCode.USER_PASSWORD_VERIFY_FAILED, body.getMessage());
            }
        } catch (HttpClientErrorException e) {
            log.warn("SSO password verify client error for user {}: {}", userId, e.getMessage());
            String errorMessage = extractSsoErrorMessage(e);
            throw new InvalidValueException(DeskErrorCode.USER_PASSWORD_VERIFY_FAILED, errorMessage);
        } catch (RestClientException e) {
            log.error("SSO password verify request failed for user {}: {}", userId, e.getMessage(), e);
            throw new ExternalServiceException(DeskErrorCode.SSO_SERVICE_ERROR, e);
        }
    }

    @Override
    public Integer getBudgetAlertThreshold(Long userRowId) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        Integer v = user.getBudgetAlertThreshold();
        return v != null ? v : 85;
    }

    /**
     * 금액 가리기 목록의 JSON 직렬화 전용.
     *
     * <p>서버는 카드 키를 해석하지 않는다 — 어떤 카드가 있는지는 화면이 정하는 어휘라
     * 서버가 알고 있으면 카드를 하나 늘릴 때마다 배포가 묶인다. 여기서는 문자열 목록을
     * 그대로 담았다 빼기만 한다.
     */
    private static final ObjectMapper HIDE_CARDS_JSON = JsonMapper.builder().build();

    @Override
    public UserApiDto.HideCardsResponse getHideCards(Long userRowId) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        return new UserApiDto.HideCardsResponse(readHideCards(user.getHideCards(), userRowId));
    }

    @Override
    @Transactional
    public UserApiDto.HideCardsResponse updateHideCards(Long userRowId, UserApiDto.UpdateHideCardsReq req) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        // 중복은 여기서 걷는다 — 두 클라이언트가 같은 카드를 각자 올려도 목록이 부풀지 않는다.
        List<String> cards = req.hideCards().stream().distinct().toList();
        user.updateHideCards(HIDE_CARDS_JSON.writeValueAsString(cards));
        return new UserApiDto.HideCardsResponse(cards);
    }

    /**
     * 저장된 JSON 을 목록으로. 저장된 적 없으면 {@code null} 을 그대로 돌려준다.
     *
     * <p><b>{@code null} 을 빈 목록으로 바꾸지 않는다.</b> 클라이언트는 "아직 안 올림"
     * ({@code null})과 "사용자가 다 풀었음"(빈 목록)을 구분해 전자면 자기 로컬 값을 올린다.
     * 여기서 뭉개면 배포 첫 실행에 가려 뒀던 금액이 통째로 드러난다.
     */
    private List<String> readHideCards(String raw, Long userRowId) {
        if (raw == null) {
            return null;
        }
        try {
            return HIDE_CARDS_JSON.readValue(raw, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            // 깨진 값이면 빈 목록으로 준다 — null 로 주면 클라이언트가 "아직 안 올림" 으로 읽고
            // 자기 로컬 값을 올려 깨진 값을 덮는데, 그건 사용자가 고른 적 없는 상태다.
            log.error("hide_cards JSON 을 읽지 못했다 — 빈 목록으로 준다. userRowId={}", userRowId, e);
            return List.of();
        }
    }

    @Override
    public PreferencesResponse getPreferences(Long userRowId) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        return PreferencesResponse.from(user);
    }

    @Override
    @Transactional
    public PreferencesResponse updatePreferences(Long userRowId, UpdatePreferencesReq req) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        user.updateBudgetAlertThreshold(req.getBudgetAlertThreshold());
        user.updateNotificationPreferences(
            req.getPushEnabled(),
            req.getNotifyPayment(),
            req.getNotifyBudget(),
            req.getNotifyAutoRecord(),
            req.getNotifyDutchPay(),
            req.getNotifyCalendar(),
            req.getNotifyWeeklyReport(),
            req.getNotifyMonthlyReport(),
            req.getQuietHoursEnabled(),
            req.getQuietHoursStart(),
            req.getQuietHoursEnd(),
            req.getNotificationSound(),
            req.getVibrationEnabled(),
            req.getEmailEnabled(),
            req.getEmailFrequency()
        );
        // 지역은 저장 전에 검증한다 — 알 수 없는 값이 들어가면 이후 모든 날짜 판정이 폴백으로 흐른다.
        if (req.getTimezone() != null && !req.getTimezone().isBlank()) {
            try {
                ZoneId.of(req.getTimezone());
            } catch (DateTimeException e) {
                throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
            }
            user.updateTimezone(req.getTimezone());
        }
        log.info("알림 환경설정 변경: userRowId={}", userRowId);
        return PreferencesResponse.from(user);
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
        return "비밀번호를 바꾸지 못했어요";
    }
}
