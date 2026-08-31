package com.porest.desk.user.service;

import com.porest.core.controller.ApiResponse;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.security.client.SsoOAuth2Client;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import com.porest.desk.user.controller.dto.UserApiDto;
import com.porest.desk.user.controller.dto.UserApiDto.UpdatePreferencesReq;

/**
 * 사용자 서비스 단위 테스트 — 비밀번호 변경/검증 SSO 프록시(client_credentials 서비스 토큰 + body userId) 및
 * 예산 알림 임계값 조회(기본값/조회 실패).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private SsoOAuth2Client ssoOAuth2Client;
    @Mock private UserRepository userRepository;
    @Mock private RestTemplate ssoRestTemplate;

    @InjectMocks private UserServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final String SVC_TOKEN = "svc-token";
    private static final String CHANGE_PATH = "/api/v1/auth/password/change";
    private static final String VERIFY_PATH = "/api/v1/auth/password/verify";

    private User userWithThreshold(Integer threshold) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        ReflectionTestUtils.setField(u, "budgetAlertThreshold", threshold);
        return u;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<HttpEntity<Map<String, String>>> entityCaptor() {
        return ArgumentCaptor.forClass(HttpEntity.class);
    }

    // ==================== changePassword ====================

    @Test
    @DisplayName("changePassword — client_credentials 서비스 토큰을 Bearer 로, body 에 userId 포함해 SSO 호출")
    void changePassword_sendsServiceTokenAndBodyUserId() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(CHANGE_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(ApiResponse.success()));

        sut.changePassword("u1", "curPw", "newPw", "newPw");

        ArgumentCaptor<HttpEntity<Map<String, String>>> captor = entityCaptor();
        org.mockito.Mockito.verify(ssoRestTemplate).exchange(eq(CHANGE_PATH), eq(HttpMethod.POST),
                captor.capture(), any(ParameterizedTypeReference.class));

        HttpEntity<Map<String, String>> entity = captor.getValue();
        assertThat(entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + SVC_TOKEN);
        Map<String, String> body = entity.getBody();
        assertThat(body).containsEntry("userId", "u1")
                .containsEntry("currentPassword", "curPw")
                .containsEntry("newPassword", "newPw")
                .containsEntry("confirmPassword", "newPw");
    }

    @Test
    @DisplayName("changePassword — SSO 응답 success=false 면 InvalidValueException")
    void changePassword_throwsWhenSsoNotSuccess() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(CHANGE_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(ApiResponse.error("FAIL", "현재 비밀번호가 일치하지 않습니다")));

        assertThatThrownBy(() -> sut.changePassword("u1", "curPw", "newPw", "newPw"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("changePassword — SSO 4xx(HttpClientErrorException) 면 InvalidValueException 으로 변환")
    void changePassword_throwsOnClientError() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(CHANGE_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, "{\"message\":\"잘못된 요청\"}".getBytes(), null));

        assertThatThrownBy(() -> sut.changePassword("u1", "curPw", "newPw", "newPw"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("changePassword — SSO 연결 실패(RestClientException) 면 ExternalServiceException")
    void changePassword_throwsOnTransportError() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(CHANGE_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> sut.changePassword("u1", "curPw", "newPw", "newPw"))
                .isInstanceOf(ExternalServiceException.class);
    }

    // ==================== verifyPassword ====================

    @Test
    @DisplayName("verifyPassword — client_credentials 서비스 토큰을 Bearer 로, body 에 userId/password 포함해 SSO 호출")
    void verifyPassword_sendsServiceTokenAndBodyUserId() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(VERIFY_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(ApiResponse.success()));

        sut.verifyPassword("u1", "myPw");

        ArgumentCaptor<HttpEntity<Map<String, String>>> captor = entityCaptor();
        org.mockito.Mockito.verify(ssoRestTemplate).exchange(eq(VERIFY_PATH), eq(HttpMethod.POST),
                captor.capture(), any(ParameterizedTypeReference.class));

        HttpEntity<Map<String, String>> entity = captor.getValue();
        assertThat(entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + SVC_TOKEN);
        assertThat(entity.getBody()).containsEntry("userId", "u1").containsEntry("password", "myPw");
    }

    @Test
    @DisplayName("verifyPassword — SSO 응답 success=false 면 InvalidValueException")
    void verifyPassword_throwsWhenSsoNotSuccess() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(VERIFY_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(ApiResponse.error("FAIL", "비밀번호가 일치하지 않습니다")));

        assertThatThrownBy(() -> sut.verifyPassword("u1", "myPw"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("verifyPassword — SSO 4xx(HttpClientErrorException) 면 InvalidValueException 으로 변환")
    void verifyPassword_throwsOnClientError() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(VERIFY_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, "{\"message\":\"비밀번호 불일치\"}".getBytes(), null));

        assertThatThrownBy(() -> sut.verifyPassword("u1", "myPw"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("verifyPassword — SSO 연결 실패(RestClientException) 면 ExternalServiceException")
    void verifyPassword_throwsOnTransportError() {
        given(ssoOAuth2Client.issueServiceToken()).willReturn(SVC_TOKEN);
        given(ssoRestTemplate.exchange(eq(VERIFY_PATH), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .willThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> sut.verifyPassword("u1", "myPw"))
                .isInstanceOf(ExternalServiceException.class);
    }

    // ==================== getBudgetAlertThreshold ====================

    @Test
    @DisplayName("getBudgetAlertThreshold — 설정값을 반환한다")
    void returnsConfiguredThreshold() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithThreshold(90)));

        assertThat(sut.getBudgetAlertThreshold(USER_ID)).isEqualTo(90);
    }

    @Test
    @DisplayName("getBudgetAlertThreshold — 값이 없으면 기본값 85")
    void returnsDefaultWhenNull() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithThreshold(null)));

        assertThat(sut.getBudgetAlertThreshold(USER_ID)).isEqualTo(85);
    }

    @Test
    @DisplayName("getBudgetAlertThreshold — 사용자가 없으면 NotFound")
    void throwsWhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getBudgetAlertThreshold(USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ==================== updatePreferences — 지역(타임존) ====================

    @Test
    @DisplayName("updatePreferences — 타임존을 바꾸면 저장되고 응답에 실린다")
    void updatesTimezone() {
        User u = userWithThreshold(85);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        UpdatePreferencesReq req = new UpdatePreferencesReq();
        ReflectionTestUtils.setField(req, "timezone", "America/New_York");

        var resp = sut.updatePreferences(USER_ID, req);

        assertThat(u.getTimezone()).isEqualTo("America/New_York");
        assertThat(resp.timezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("updatePreferences — 타임존 미전송이면 기존 값을 유지한다(부분 수정)")
    void keepsTimezoneWhenAbsent() {
        User u = userWithThreshold(85);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        var resp = sut.updatePreferences(USER_ID, new UpdatePreferencesReq());

        assertThat(u.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(resp.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("updatePreferences — 알 수 없는 타임존이면 거부한다(저장 전 검증)")
    void rejectsUnknownTimezone() {
        User u = userWithThreshold(85);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        UpdatePreferencesReq req = new UpdatePreferencesReq();
        ReflectionTestUtils.setField(req, "timezone", "Mars/Olympus");

        assertThatThrownBy(() -> sut.updatePreferences(USER_ID, req))
                .isInstanceOf(InvalidValueException.class);
        assertThat(u.getTimezone()).isEqualTo("Asia/Seoul");
    }

    // ── 금액 가리기 ──────────────────────────────────────────────────────

    private User userWithHideCards(String raw) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        ReflectionTestUtils.setField(u, "hideCards", raw);
        return u;
    }

    @Test
    @DisplayName("올린 적 없으면 null 을 그대로 준다 — 빈 목록으로 뭉개면 첫 실행에 금액이 드러난다")
    void getHideCards_neverSynced_returnsNull() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithHideCards(null)));

        // null(아직 안 올림)과 []( 사용자가 다 풀었음)는 뜻이 다르다. 클라이언트는 전자면
        // 내려받지 않고 자기 로컬 값을 올린다 — 여기서 []로 바꾸면 그 분기가 사라진다.
        assertThat(sut.getHideCards(USER_ID).hideCards()).isNull();
    }

    @Test
    @DisplayName("사용자가 다 푼 상태는 빈 목록으로 준다 — null 과 구분된다")
    void getHideCards_explicitlyEmpty_returnsEmptyList() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithHideCards("[]")));

        assertThat(sut.getHideCards(USER_ID).hideCards()).isEmpty();
    }

    @Test
    @DisplayName("저장된 목록을 그대로 돌려준다 — 서버는 카드 키를 해석하지 않는다")
    void getHideCards_returnsStoredCards() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(userWithHideCards("[\"asset.netWorth\",\"kind.expense\"]")));

        assertThat(sut.getHideCards(USER_ID).hideCards())
                .containsExactly("asset.netWorth", "kind.expense");
    }

    @Test
    @DisplayName("깨진 값은 빈 목록으로 준다 — null 로 주면 클라이언트가 로컬 값으로 덮어쓴다")
    void getHideCards_brokenJson_returnsEmptyList() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithHideCards("not json")));

        assertThat(sut.getHideCards(USER_ID).hideCards()).isEmpty();
    }

    @Test
    @DisplayName("교체 저장 — 중복은 걷는다(두 클라이언트가 같은 카드를 각자 올려도 부풀지 않는다)")
    void updateHideCards_dedupesAndStores() {
        User user = userWithHideCards(null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        var res = sut.updateHideCards(USER_ID,
                new UserApiDto.UpdateHideCardsReq(List.of("a", "b", "a")));

        assertThat(res.hideCards()).containsExactly("a", "b");
        assertThat(user.getHideCards()).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    @DisplayName("빈 목록으로 교체하면 null 이 아니라 [] 가 저장된다 — '다 풀었음' 이 기록돼야 한다")
    void updateHideCards_empty_storesEmptyArrayNotNull() {
        User user = userWithHideCards("[\"a\"]");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        sut.updateHideCards(USER_ID, new UserApiDto.UpdateHideCardsReq(List.of()));

        // null 이 되면 다음 로그인에서 "아직 안 올림" 으로 읽혀 옛 로컬 값이 되살아난다.
        assertThat(user.getHideCards()).isEqualTo("[]");
    }
}
