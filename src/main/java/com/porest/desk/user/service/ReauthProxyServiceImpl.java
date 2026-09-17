package com.porest.desk.user.service;

import com.porest.core.controller.ApiResponse;
import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.client.SsoOAuth2Client;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReauthProxyServiceImpl implements ReauthProxyService {

    private static final String SSO_EMAIL_CODE_PATH = "/api/v1/auth/reauth/email-code";
    private static final String SSO_EMAIL_CODE_VERIFY_PATH = "/api/v1/auth/reauth/email-code/verify";
    private static final String SSO_PASSWORD_PATH = "/api/v1/auth/reauth/password";

    /**
     * 티켓이 무엇을 위한 것인지. desk 에서 재인증을 요구하는 자리는 <b>지금 해지 하나뿐</b>
     * 이라 서버가 박는다 — 클라이언트가 고르게 하면 아무 용도의 티켓이나 발급받을 수 있고,
     * {@link ReauthTicketVerifier} 가 용도를 대조하는 의미가 없어진다.
     * 두 번째 용도가 생기면 그때 파라미터로 연다.
     */
    private static final String PURPOSE_WITHDRAW = "withdraw";

    private final SsoOAuth2Client ssoOAuth2Client;

    @Qualifier("ssoRestTemplate")
    private final RestTemplate ssoRestTemplate;

    @Override
    public void sendEmailCode(String userId) {
        call(SSO_EMAIL_CODE_PATH, Map.of("userId", userId), userId, "코드 발송");
    }

    @Override
    public String verifyEmailCode(String userId, String code) {
        VerifyResp resp = call(SSO_EMAIL_CODE_VERIFY_PATH,
                Map.of("userId", userId, "code", code, "purpose", PURPOSE_WITHDRAW),
                userId, "코드 확인");
        return ticketOf(resp, userId);
    }

    @Override
    public String verifyPassword(String userId, String password) {
        VerifyResp resp = call(SSO_PASSWORD_PATH,
                Map.of("userId", userId, "password", password, "purpose", PURPOSE_WITHDRAW),
                userId, "비밀번호 확인");
        return ticketOf(resp, userId);
    }

    /**
     * SSO 재인증 API 한 번 호출.
     *
     * <p>서비스 토큰에는 사용자 식별자가 없으므로 대상 {@code userId} 를 body 에 담아
     * 전달한다(SSO {@code ReauthApiController.resolveTarget} 계약).
     *
     * <p>4xx 는 사용자가 고칠 수 있는 것(코드·비밀번호가 틀렸다)이라 400 으로 돌려준다.
     * 그 밖의 통신 실패는 사용자가 어쩌지 못하므로 502 다 — 여기를 400 으로 뭉뚱그리면
     * SSO 가 죽었을 때 화면이 "비밀번호가 틀렸어요" 라고 거짓말을 한다.
     */
    private VerifyResp call(String path, Map<String, String> body, String userId, String what) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(ssoOAuth2Client.issueServiceToken());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ApiResponse<VerifyResp>> response = ssoRestTemplate.exchange(
                    path, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<ApiResponse<VerifyResp>>() {}
            );

            ApiResponse<VerifyResp> resp = response.getBody();
            if (resp != null && !resp.isSuccess()) {
                throw new InvalidValueException(DeskErrorCode.REAUTH_FAILED, resp.getMessage());
            }
            return resp != null ? resp.getData() : null;

        } catch (HttpClientErrorException e) {
            // 본문은 찍지 않는다 — 비밀번호·코드가 들어 있던 요청의 응답이다.
            log.warn("SSO 재인증 {} 실패(4xx). userId={} status={}", what, userId, e.getStatusCode());
            throw new InvalidValueException(DeskErrorCode.REAUTH_FAILED, extractSsoErrorMessage(e));
        } catch (RestClientException e) {
            log.error("SSO 재인증 {} 호출 실패. userId={}", what, userId, e);
            throw new ExternalServiceException(DeskErrorCode.SSO_SERVICE_ERROR, e);
        }
    }

    /**
     * 티켓이 비어 오면 실패로 본다.
     *
     * <p>{@code null} 을 그대로 돌려주면 화면은 "확인됐다" 로 읽고 다음 단계로 넘어갔다가
     * 해지 호출에서 {@code AUTH_020} 을 맞는다 — 사용자는 방금 맞게 넣은 비밀번호를
     * 의심하게 된다. 확인이 끝난 자리에서 끊는 편이 낫다.
     */
    private String ticketOf(VerifyResp resp, String userId) {
        if (resp == null || resp.reauthToken() == null || resp.reauthToken().isBlank()) {
            log.error("SSO 가 재인증 티켓 없이 성공을 돌려줬다. userId={}", userId);
            throw new ExternalServiceException(DeskErrorCode.SSO_SERVICE_ERROR);
        }
        return resp.reauthToken();
    }

    /** SSO 가 준 문장을 그대로 화면에 올린다 — 코드·비밀번호 오류 문구는 SSO 소유다. */
    private String extractSsoErrorMessage(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("\"message\"")) {
                int start = responseBody.indexOf("\"message\"") + 11;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    return responseBody.substring(start, end);
                }
            }
        } catch (Exception ex) {
            log.debug("SSO 오류 메시지를 읽지 못했다", ex);
        }
        return "본인 확인에 실패했어요";
    }

    /** SSO {@code ReauthApiController.VerifyResp} 와 같은 모양. */
    public record VerifyResp(String reauthToken) {}
}
