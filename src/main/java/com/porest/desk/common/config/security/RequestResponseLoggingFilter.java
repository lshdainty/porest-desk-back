package com.porest.desk.common.config.security;

import com.porest.core.util.HttpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 모든 HTTP 요청/응답에 대한 포괄적인 로깅을 수행하는 필터
 * - Trace ID (UUID) 생성 및 MDC 설정
 * - Request/Response Body 캡처
 * - 실행 시간 측정
 * - User ID, Client IP, User-Agent 수집
 * - 가독성 좋은 한 줄 포맷으로 로그 출력
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "requestId";
    private static final int MAX_BODY_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 50;
    private static final int CONTENT_CACHE_LIMIT = 10 * 1024; // 10KB
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/actuator/health",
            "/actuator/prometheus",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 제외할 경로는 로깅 없이 통과
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Trace ID 생성 및 MDC 설정
        String traceId = generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);

        // Request/Response Body를 여러 번 읽을 수 있도록 래핑
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // 다음 필터로 전달
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // 로그 출력
            logRequestResponse(wrappedRequest, wrappedResponse, traceId, executionTime);

            // Response Body를 실제 응답으로 복사 (중요!)
            wrappedResponse.copyBodyToResponse();

            // MDC 정리
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Trace ID 생성 (UUID 기반 8자리)
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 요청/응답 정보를 가독성 좋은 한 줄 포맷으로 로깅
     * 포맷: [traceId] | status | time | METHOD URI | IP:ip | User:user | Agent:agent | Req:body | Res:body
     */
    private void logRequestResponse(ContentCachingRequestWrapper request,
                                     ContentCachingResponseWrapper response,
                                     String traceId,
                                     long executionTime) {
        try {
            int status = response.getStatus();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String clientIp = HttpUtils.getClientIp();
            String userId = getCurrentUserId();
            String userAgent = getUserAgent(request);
            String requestBody = getRequestBody(request);
            String responseBody = getResponseBody(response);

            // URI에 쿼리스트링 포함
            String fullUri = queryString != null ? uri + "?" + queryString : uri;

            // Body 잘림 여부 확인
            boolean requestBodyTruncated = requestBody != null && requestBody.length() > MAX_BODY_LENGTH;
            boolean responseBodyTruncated = responseBody != null && responseBody.length() > MAX_BODY_LENGTH;

            // 로그 메시지 구성
            StringBuilder logMessage = new StringBuilder();
            logMessage.append(String.format("[%s] | %d | %4dms | %s %s",
                    traceId, status, executionTime, method, fullUri));

            // IP 정보
            logMessage.append(" | IP:").append(clientIp != null ? clientIp : "-");

            // 사용자 정보
            logMessage.append(" | User:").append(userId != null ? userId : "anonymous");

            // User-Agent 정보
            logMessage.append(" | Agent:").append(userAgent != null ? userAgent : "-");

            // Request Body (있는 경우만)
            if (requestBody != null && !requestBody.isEmpty()) {
                logMessage.append(" | Req:").append(truncate(requestBody, MAX_BODY_LENGTH));
            }

            // Response Body (있는 경우만)
            if (responseBody != null && !responseBody.isEmpty()) {
                logMessage.append(" | Res:").append(truncate(responseBody, MAX_BODY_LENGTH));
            }

            // 상태 코드에 따라 로그 레벨 분리
            if (status >= 500) {
                log.error("{}", logMessage);
            } else if (status >= 400) {
                log.warn("{}", logMessage);
            } else {
                log.info("{}", logMessage);
            }

            // Body가 잘린 경우 DEBUG 레벨로 전체 원본 출력
            if (requestBodyTruncated || responseBodyTruncated) {
                logFullBody(traceId, requestBody, responseBody, requestBodyTruncated, responseBodyTruncated);
            }

        } catch (Exception e) {
            log.error("Failed to log request/response", e);
        }
    }

    /**
     * 잘린 Body의 전체 원본을 DEBUG 레벨로 출력
     */
    private void logFullBody(String traceId, String requestBody, String responseBody,
                              boolean requestBodyTruncated, boolean responseBodyTruncated) {
        if (requestBodyTruncated && requestBody != null) {
            log.debug("[{}] Full Request Body: {}", traceId, sanitizeForLog(requestBody));
        }
        if (responseBodyTruncated && responseBody != null) {
            log.debug("[{}] Full Response Body: {}", traceId, sanitizeForLog(responseBody));
        }
    }

    /**
     * 로그 출력을 위해 줄바꿈 제거
     */
    private String sanitizeForLog(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\n", " ").replace("\r", "");
    }

    /**
     * User-Agent 헤더 추출 (길이 제한 적용)
     */
    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && !userAgent.isEmpty()) {
            return truncate(userAgent, MAX_USER_AGENT_LENGTH);
        }
        return null;
    }

    /**
     * 문자열을 최대 길이로 자르고 말줄임표 추가
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        str = str.replace("\n", " ").replace("\r", "");
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Request Body 추출
     */
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            String body = new String(content, StandardCharsets.UTF_8);
            return maskSensitiveData(body);
        }
        return null;
    }

    /**
     * Response Body 추출
     */
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * 현재 인증된 사용자 ID 추출
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to get current user", e);
        }
        return null;
    }

    /**
     * 민감한 정보 마스킹 (비밀번호, 토큰 등)
     */
    private String maskSensitiveData(String body) {
        if (body.contains("password") || body.contains("user_pw")) {
            body = body.replaceAll("(\"password\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3")
                      .replaceAll("(\"user_pw\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3")
                      .replaceAll("(&|\\?)password=([^&]+)", "$1password=***")
                      .replaceAll("(&|\\?)user_pw=([^&]+)", "$1user_pw=***");
        }
        return body;
    }
}
