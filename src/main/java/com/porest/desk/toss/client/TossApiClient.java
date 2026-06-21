package com.porest.desk.toss.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.toss.client.dto.TossEnvelope;
import com.porest.desk.toss.client.dto.TossErrorBody;
import com.porest.desk.toss.config.TossProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 토스증권 Open API 저수준 호출 클라이언트.<br>
 * 모든 조회 GET 요청에 대해 (1) 액세스 토큰 자동 주입, (2) 성공 envelope({@code result}) 언래핑,
 * (3) 토큰 만료(401) 1회 재발급 재시도, (4) 에러 응답을 {@link ExternalServiceException} 으로 변환을 담당한다.
 *
 * <p>계좌/자산 관련 엔드포인트는 {@code X-Tossinvest-Account} 헤더로 계좌 식별자(accountSeq)를 함께 전달한다.</p>
 *
 * <p>경로/쿼리의 동적 값은 모두 URI 변수({@code {placeholder}})로 바인딩해 RestTemplate 이 정확히 1회만
 * 인코딩하도록 한다(동적 path 세그먼트를 문자열로 직접 결합하면 인코딩 누락·template 인젝션 위험).</p>
 */
@Slf4j
@Component
public class TossApiClient {

    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    private final RestTemplate tossRestTemplate;
    private final TossTokenManager tokenManager;
    private final TossProperties tossProperties;

    public TossApiClient(@Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
                         TossTokenManager tokenManager,
                         TossProperties tossProperties) {
        this.tossRestTemplate = tossRestTemplate;
        this.tokenManager = tokenManager;
        this.tossProperties = tossProperties;
    }

    /**
     * 계좌 비귀속 조회(시세·종목·시장정보·계좌목록).
     */
    public <T> T get(String path, MultiValueMap<String, String> query,
                     ParameterizedTypeReference<TossEnvelope<T>> typeRef) {
        return get(path, query, null, typeRef);
    }

    /**
     * 계좌 귀속 조회(보유주식 등). accountSeq 가 있으면 {@code X-Tossinvest-Account} 헤더를 추가한다.
     */
    public <T> T get(String path, MultiValueMap<String, String> query, Long accountSeq,
                     ParameterizedTypeReference<TossEnvelope<T>> typeRef) {
        ensureConfigured();
        return execute(path, null, query, accountSeq, typeRef, true);
    }

    /**
     * 경로에 동적 세그먼트가 있는 조회(예: {@code /api/v1/stocks/{symbol}/warnings}).
     * pathVars 의 값은 URI 변수로 안전하게 인코딩된다.
     */
    public <T> T getPath(String pathTemplate, Map<String, ?> pathVars,
                         ParameterizedTypeReference<TossEnvelope<T>> typeRef) {
        ensureConfigured();
        return execute(pathTemplate, pathVars, null, null, typeRef, true);
    }

    private void ensureConfigured() {
        if (!tossProperties.isConfigured()) {
            log.warn("토스증권 연동 설정(app.toss.base-url/client-id/client-secret)이 비어 있습니다");
            throw new ExternalServiceException(DeskErrorCode.TOSS_NOT_CONFIGURED);
        }
    }

    private <T> T execute(String pathTemplate, Map<String, ?> pathVars, MultiValueMap<String, String> query,
                          Long accountSeq, ParameterizedTypeReference<TossEnvelope<T>> typeRef,
                          boolean retryOnUnauthorized) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenManager.getAccessToken());
        if (accountSeq != null) {
            headers.set(ACCOUNT_HEADER, String.valueOf(accountSeq));
        }

        Map<String, Object> uriVars = new HashMap<>();
        if (pathVars != null) {
            uriVars.putAll(pathVars);
        }
        String uriTemplate = buildUriTemplate(pathTemplate, query, uriVars);

        try {
            ResponseEntity<TossEnvelope<T>> response = tossRestTemplate.exchange(
                    uriTemplate, HttpMethod.GET, new HttpEntity<>(headers), typeRef, uriVars);
            TossEnvelope<T> body = response.getBody();
            return body == null ? null : body.result();
        } catch (HttpClientErrorException.Unauthorized e) {
            if (retryOnUnauthorized) {
                // 토큰이 무효화되었을 수 있으므로 1회 재발급 후 재시도한다.
                log.debug("토스증권 401 - 토큰 재발급 후 재시도");
                tokenManager.invalidate();
                return execute(pathTemplate, pathVars, query, accountSeq, typeRef, false);
            }
            throw toExternalException(e, pathTemplate);
        } catch (HttpStatusCodeException e) {
            throw toExternalException(e, pathTemplate);
        } catch (RestClientException e) {
            log.error("토스증권 API 호출 실패: {}", pathTemplate, e);
            throw new ExternalServiceException(DeskErrorCode.TOSS_API_ERROR, e);
        }
    }

    /**
     * 에러 응답 본문({@code { "error": {...} }})에서 상세를 추출해 <b>로그에만</b> 남기고,
     * 클라이언트에는 i18n 메시지(error.toss.api.error)만 내려가는 예외로 변환한다.
     * 업스트림 토스 에러 본문(code/message)을 그대로 응답에 릴레이하지 않는다.
     */
    private ExternalServiceException toExternalException(HttpStatusCodeException e, String path) {
        String detail = null;
        try {
            TossErrorBody errorBody = e.getResponseBodyAs(TossErrorBody.class);
            if (errorBody != null && errorBody.error() != null) {
                TossErrorBody.ApiError err = errorBody.error();
                detail = "code=" + err.code() + ", message=" + err.message() + ", requestId=" + err.requestId();
            }
        } catch (Exception ignore) {
            // 에러 본문 파싱 실패는 무시하고 상태코드 기반으로만 로깅한다.
        }
        log.error("토스증권 API 오류: path={}, status={}, {}", path, e.getStatusCode().value(),
                detail != null ? detail : "(본문 파싱 불가)", e);
        return new ExternalServiceException(DeskErrorCode.TOSS_API_ERROR, e);
    }

    /**
     * baseUrl 은 {@code tossRestTemplate} 의 UriTemplateHandler 가 prepend 한다.
     * 쿼리 값은 placeholder 로 바인딩해 RestTemplate 이 한 번만 인코딩하도록 한다.
     */
    private String buildUriTemplate(String pathTemplate, MultiValueMap<String, String> query, Map<String, Object> uriVars) {
        if (query == null || query.isEmpty()) {
            return pathTemplate;
        }
        StringBuilder sb = new StringBuilder(pathTemplate);
        boolean first = true;
        int idx = 0;
        for (Map.Entry<String, List<String>> entry : query.entrySet()) {
            for (String value : entry.getValue()) {
                if (value == null) {
                    continue;
                }
                sb.append(first ? '?' : '&');
                String placeholder = "q" + (idx++);
                sb.append(entry.getKey()).append('=').append('{').append(placeholder).append('}');
                uriVars.put(placeholder, value);
                first = false;
            }
        }
        return sb.toString();
    }
}
