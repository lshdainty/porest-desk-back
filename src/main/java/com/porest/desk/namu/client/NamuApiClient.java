package com.porest.desk.namu.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.logging.UpstreamErrorLog;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuResponse;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.type.SecuritiesBroker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 나무증권(NH PLUG) 저수준 호출 클라이언트.
 *
 * <p><b>토스 클라이언트를 복사하면 안 되는 이유가 셋 있다.</b>
 *
 * <ol>
 *   <li><b>전부 POST + JSON</b> — 조회도 GET 이 아니다. 요청 본문은 {@code {"Input_0": {...}}} 봉투.</li>
 *   <li><b>HTTP 200 으로도 실패한다</b> — 성공 여부는 {@code rsp_cd} 로만 안다
 *       ({@link NamuResponse#isSuccess()}). 상태코드만 보면 조용히 빈 화면이 된다.</li>
 *   <li><b>인증 헤더가 Bearer 하나가 아니다</b> — 매 호출에 평문 키/시크릿이 함께 간다.
 *       그 구성은 {@code BrokerTokenManager} 가 맡는다.</li>
 * </ol>
 *
 * <p>업스트림 에러 본문({@code rsp_msg})은 <b>로그에만</b> 남긴다. 사용자에게는 i18n 메시지만 나간다.
 * 그 로그도 {@link UpstreamErrorLog} 를 지나야 한다 — 나무 응답에는 {@code cust_no}·{@code acct_no} 가
 * 섞이고, 이 자리는 {@code RequestResponseLoggingFilter} 의 마스킹을 안 거친다.
 */
@Slf4j
@Component
public class NamuApiClient {

    private final RestTemplate namuRestTemplate;
    private final BrokerTokenManager tokenManager;
    private final NamuProperties namuProperties;

    public NamuApiClient(@Qualifier("namuRestTemplate") RestTemplate namuRestTemplate,
                         BrokerTokenManagers tokenManagers,
                         NamuProperties namuProperties) {
        this.namuRestTemplate = namuRestTemplate;
        this.tokenManager = tokenManagers.of(SecuritiesBroker.NAMU);
        this.namuProperties = namuProperties;
    }

    /**
     * {@code Output_0} 이 <b>객체</b>인 조회. 시세 계열이 이쪽이다.
     *
     * @param typeRef {@code NamuEnvelope<페이로드>} 의 타입 참조
     */
    public <T> T postObject(Long userRowId, String path, Map<String, ?> input,
                            ParameterizedTypeReference<NamuEnvelope<T>> typeRef) {
        return exchange(userRowId, path, input, typeRef).output0();
    }

    /**
     * {@code Output_0} 이 <b>배열</b>인 조회. 우리가 쓰는 것 중에는 계좌목록 하나뿐이다.
     *
     * @param typeRef {@code NamuListEnvelope<페이로드>} 의 타입 참조
     */
    public <T> java.util.List<T> postList(Long userRowId, String path, Map<String, ?> input,
                                          ParameterizedTypeReference<NamuListEnvelope<T>> typeRef) {
        return exchange(userRowId, path, input, typeRef).resultOrEmpty();
    }

    /**
     * 조회 1건 — 봉투를 통째로 돌려준다. 요약({@code Output_0}) + 목록({@code Output_1})이
     * 함께 오는 잔고 계열에 쓴다.
     */
    public <R extends NamuResponse> R exchange(Long userRowId, String path, Map<String, ?> input,
                                               ParameterizedTypeReference<R> typeRef) {
        ensureConfigured();
        return execute(userRowId, path, input, typeRef, true);
    }

    private void ensureConfigured() {
        if (!namuProperties.isConfigured()) {
            log.warn("나무증권 연동 설정(app.namu.base-url)이 비어 있습니다");
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_NOT_CONFIGURED);
        }
    }

    private <R extends NamuResponse> R execute(Long userRowId, String path, Map<String, ?> input,
                                               ParameterizedTypeReference<R> typeRef,
                                               boolean retryOnUnauthorized) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(tokenManager.authHeaders(userRowId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<R> response = namuRestTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(Map.of("Input_0", input), headers), typeRef);

            R body = response.getBody();
            if (body == null) {
                log.error("나무증권 응답 본문 없음: path={}", path);
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR);
            }
            // 200 이어도 rsp_cd 를 봐야 한다. 이 검사가 빠지면 실패가 '결과 없음' 으로 둔갑한다.
            if (!body.isSuccess()) {
                // rsp_msg 는 나무가 만든 자유 문장이라 계좌번호가 섞여 온다. rsp_cd 는 코드라 그대로 둔다.
                log.error("나무증권 API 오류: path={}, rsp_cd={}, rsp_msg={}",
                    path, body.rspCd(), UpstreamErrorLog.safe(body.rspMsg()));
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR);
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized e) {
            // 토큰이 무효라고 증권사가 말한 유일한 신호가 401 이다. 버릴지 말지는
            // 토큰 담당자가 정한다 — 방금 발급한 토큰이면 버려도 같은 401 이 오고
            // 알림톡만 쌓인다(시세는 종목마다 한 콜이라 폴링 한 바퀴에 종목 수만큼).
            if (retryOnUnauthorized && tokenManager.invalidateOnUnauthorized(userRowId)) {
                log.info("나무증권 401 - 토큰 재발급 후 1회 재시도: path={}, userRowId={}", path, userRowId);
                return execute(userRowId, path, input, typeRef, false);
            }
            throw toExternalException(e, path);
        } catch (HttpStatusCodeException e) {
            // 429(한도 초과)·5xx·기타 4xx 는 토큰 문제가 아니다. 캐시를 그대로 둔다.
            throw toExternalException(e, path);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패도 토큰 문제가 아니다 — 여기서 무효화하면 멀쩡한 토큰을 버린다.
            RestClientException safe = UpstreamErrorLog.redact(e);
            log.error("나무증권 API 호출 실패: path={}, 원인={}", path, safe.getMessage());
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR, safe);
        }
    }

    /**
     * 상태코드로 실패한 호출을 우리 예외로 바꾼다.
     *
     * <p><b>예외 객체를 로그에도 cause 에도 그대로 넘기지 않는다.</b> Spring 은 응답 본문을
     * 예외 메시지에 통째로 싣고({@code 400 Bad Request: "{...}"}), core 의 예외 핸들러는
     * {@code ExternalServiceException} 을 객체째 찍는다 — 둘이 겹치면 나무가 돌려준
     * {@code cust_no}·{@code acct_no} 가 {@code Caused by:} 줄에 그대로 남는다.
     * 진단에 필요한 것(원본 예외 이름 · 상태코드 · path)은 가린 뒤에도 남는다.
     *
     * <p><b>429 만 타입을 나눈다</b>({@link NamuRateLimitException}). 다른 실패는 "이번
     * 호출이 실패했다" 지만 429 는 "지금 너무 많이 부르고 있다" 라, 호출부가 <b>다시 치지
     * 않기로</b> 결정할 수 있어야 한다. 에러코드는 같으므로 클라이언트에 나가는 응답은 그대로다.
     */
    private ExternalServiceException toExternalException(HttpStatusCodeException e, String path) {
        RestClientException safe = UpstreamErrorLog.redact(e);
        log.error("나무증권 API 오류: path={}, status={}, 원인={}",
            path, e.getStatusCode().value(), safe.getMessage());
        return e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
            ? new NamuRateLimitException(safe)
            : new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR, safe);
    }
}
