package com.porest.desk.namu.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.namu.client.dto.NamuEnvelope;
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
 *   <li><b>HTTP 200 으로도 실패한다</b> — {@code rsp_cd} 가 {@code "00000"} 이 아니면 실패다.
 *       상태코드만 보면 조용히 빈 화면이 된다.</li>
 *   <li><b>인증 헤더가 Bearer 하나가 아니다</b> — 매 호출에 평문 키/시크릿이 함께 간다.
 *       그 구성은 {@code BrokerTokenManager} 가 맡는다.</li>
 * </ol>
 *
 * <p>업스트림 에러 본문({@code rsp_msg})은 <b>로그에만</b> 남긴다. 사용자에게는 i18n 메시지만 나간다.
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
     * 조회 1건. {@code input} 이 {@code Input_0} 안에 들어가고 {@code Output_0} 만 꺼내 돌려준다.
     *
     * @param typeRef {@code NamuEnvelope<페이로드>} 의 타입 참조
     */
    public <T> java.util.List<T> post(Long userRowId, String path, Map<String, ?> input,
                                      ParameterizedTypeReference<NamuEnvelope<T>> typeRef) {
        ensureConfigured();
        return execute(userRowId, path, input, typeRef, true);
    }

    private void ensureConfigured() {
        if (!namuProperties.isConfigured()) {
            log.warn("나무증권 연동 설정(app.namu.base-url)이 비어 있습니다");
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_NOT_CONFIGURED);
        }
    }

    private <T> java.util.List<T> execute(Long userRowId, String path, Map<String, ?> input,
                                          ParameterizedTypeReference<NamuEnvelope<T>> typeRef,
                                          boolean retryOnUnauthorized) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(tokenManager.authHeaders(userRowId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<NamuEnvelope<T>> response = namuRestTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(Map.of("Input_0", input), headers), typeRef);

            NamuEnvelope<T> body = response.getBody();
            if (body == null) {
                log.error("나무증권 응답 본문 없음: path={}", path);
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR);
            }
            // 200 이어도 rsp_cd 를 봐야 한다. 이 검사가 빠지면 실패가 '결과 없음' 으로 둔갑한다.
            if (!body.isSuccess()) {
                log.error("나무증권 API 오류: path={}, rsp_cd={}, rsp_msg={}", path, body.rspCd(), body.rspMsg());
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR);
            }
            return body.resultOrEmpty();
        } catch (HttpClientErrorException.Unauthorized e) {
            if (retryOnUnauthorized) {
                // 토큰이 무효화되었을 수 있으므로 1회 재발급 후 재시도한다.
                log.debug("나무증권 401 - 토큰 재발급 후 재시도");
                tokenManager.invalidate(userRowId);
                return execute(userRowId, path, input, typeRef, false);
            }
            throw toExternalException(e, path);
        } catch (HttpStatusCodeException e) {
            throw toExternalException(e, path);
        } catch (RestClientException e) {
            log.error("나무증권 API 호출 실패: {}", path, e);
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR, e);
        }
    }

    private ExternalServiceException toExternalException(HttpStatusCodeException e, String path) {
        log.error("나무증권 API 오류: path={}, status={}", path, e.getStatusCode().value(), e);
        return new ExternalServiceException(DeskErrorCode.SECURITIES_API_ERROR, e);
    }
}
