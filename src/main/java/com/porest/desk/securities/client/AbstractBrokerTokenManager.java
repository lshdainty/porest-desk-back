package com.porest.desk.securities.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 증권사 인증의 공통 부분. 증권사별 구현이 이걸 상속해 쓴다.
 *
 * <p>여기 있는 것 — 크리덴셜 조회·복호화, 토큰 캐시 조회/저장/무효화, 만료 버퍼,
 * 사용자별 발급 직렬화, {@link RestClientException} → {@link ExternalServiceException} 변환.
 * 증권사가 달라도 같은 부분이라 한 벌만 둔다.
 *
 * <p>자식이 재정의하는 것은 <b>{@link #issueToken}</b> 하나다. 인증 헤더가 Bearer 하나로
 * 끝나지 않는 증권사만 {@link #applyAuth} 를 추가로 재정의한다 — 나머지는 부모 것을 그대로
 * 쓰므로 기존 파일을 손대지 않는다.
 *
 * <p>평문 키/시크릿은 이 클래스 안에서만 산다. 캐시에는 토큰만 넣는다 — 평문을 장수 맵에
 * 들고 있으면 힙 덤프 한 번에 전 사용자 키가 새어 나간다.
 *
 * <p><b>발급 1회 = 사용자에게 알림톡 1건.</b> 나무증권은 재발급 때마다 사용자 휴대폰으로
 * 알림톡을 보낸다. 그래서 이 클래스의 설계 목표는 "동작한다" 가 아니라 <b>"발급 횟수를
 * 최소로 유지한다"</b> 다. 그 목표를 지키는 장치가 넷이다.
 *
 * <ol>
 *   <li><b>토큰을 버리는 경우를 좁힌다</b> — 증권사가 "이 토큰은 무효다"(HTTP 401) 라고
 *       말했을 때만 버린다. 통신 오류·타임아웃·한도 초과(429)·업무 오류({@code rsp_cd})는
 *       토큰 문제가 아니므로 캐시를 그대로 둔다.</li>
 *   <li><b>재발급 쿨다운</b> — {@link #invalidateOnUnauthorized}. 방금 발급한 토큰으로도
 *       401 이 나면 토큰이 원인이 아니다(권한·차단·업스트림 장애). 그때 계속 재발급하면
 *       호출 수만큼 알림톡이 쌓인다.</li>
 *   <li><b>사용자별 발급 직렬화</b> — 캐시가 빈 상태로 동시 요청 N 개가 오면 발급도 N 번
 *       나간다. 사용자 단위 락으로 1번으로 접는다. <b>사용자끼리는 막지 않는다.</b></li>
 *   <li><b>발급 로그를 info 로</b> — 발급은 알림톡이 나가는 사건이라 "언제·왜" 가 로그에
 *       남아야 한다. 토큰·앱키·시크릿 값은 절대 남기지 않는다.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBrokerTokenManager implements BrokerTokenManager {

    /**
     * 만료 직전 토큰으로 호출이 나가 401 을 맞는 걸 막는 여유.
     * 증권사 시계와 우리 시계가 몇 초 어긋나도 견딘다.
     */
    protected static final long EXPIRY_BUFFER_SECONDS = 60L;

    /**
     * 재발급 쿨다운. 이 시간 안에 발급한 토큰으로 401 이 나면 재발급하지 않고 그냥 실패시킨다.
     *
     * <p>자산 화면 폴링 주기(10초)보다 넉넉히 길어야 한다. 짧으면 폴링 한 바퀴에 종목 수만큼
     * 발급이 나가던 증폭이 그대로 살아난다.
     */
    protected static final long REISSUE_COOLDOWN_SECONDS = 60L;

    private static final String MASK = "***";

    private final UserSecuritiesCredentialRepository credentialRepository;
    private final AesGcmCipher cipher;
    private final BrokerTokenStore tokenStore;

    /**
     * 사용자별 발급 상태. 락·마지막 발급 시각·다음 발급 사유를 한 객체로 묶어 맵 하나로 든다.
     *
     * <p>엔트리를 지우지 않는다 — 지우는 순간 그 사용자의 락 객체가 새로 만들어져 직렬화가
     * 깨질 수 있고, 엔트리는 사용자당 수십 바이트에 크리덴셜을 등록한 사용자 수만큼만 생긴다.
     */
    private final Map<Long, IssueState> issueStates = new ConcurrentHashMap<>();

    /** 복호화한 평문 한 쌍. 이 클래스 밖으로 나가지 않는다. */
    protected record ApiCredential(String apiKey, String apiSecret) {
        /** 자동 toString 이 평문을 노출하지 않게 고정한다. */
        @Override
        public String toString() {
            return "ApiCredential[apiKey=***, apiSecret=***]";
        }
    }

    /** 발급 사유. 로그로만 나간다 — 알림톡이 쌓일 때 원인을 즉시 알기 위한 것. */
    private enum IssueReason {
        INITIAL("최초/만료"),
        UNAUTHORIZED("401-토큰무효"),
        REGISTER("키등록");

        private final String label;

        IssueReason(String label) {
            this.label = label;
        }
    }

    private static final class IssueState {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long lastIssuedAtMillis;
        private volatile IssueReason pendingReason;
    }

    // ── 증권사별로 다른 것 ────────────────────────────────────────────────

    /**
     * 이 증권사의 토큰 발급 프로토콜. 실패는 {@link RestClientException} 으로 던지면
     * 부모가 {@code SECURITIES_AUTH_ERROR} 로 바꾼다.
     */
    protected abstract BrokerToken issueToken(String apiKey, String apiSecret);

    /**
     * 인증 헤더 구성. 기본은 {@code Authorization: Bearer} 하나 — 대부분 이걸로 끝난다.
     * 나무처럼 평문 키/시크릿을 매 호출 요구하는 증권사만 재정의한다.
     */
    protected void applyAuth(HttpHeaders headers, String accessToken, String apiKey, String apiSecret) {
        headers.setBearerAuth(accessToken);
    }

    /** 쿨다운 길이. 테스트가 시계를 기다리지 않고 만료 후 동작을 보게 열어 둔다. */
    protected long reissueCooldownMillis() {
        return REISSUE_COOLDOWN_SECONDS * 1000L;
    }

    // ── 공통 ─────────────────────────────────────────────────────────────

    @Override
    public String getAccessToken(Long userRowId) {
        return tokenStore.get(broker(), userRowId).orElseGet(() -> issueFor(userRowId, activeCredential(userRowId)));
    }

    @Override
    public HttpHeaders authHeaders(Long userRowId) {
        ApiCredential cred = activeCredential(userRowId);
        String token = tokenStore.get(broker(), userRowId).orElseGet(() -> issueFor(userRowId, cred));
        HttpHeaders headers = new HttpHeaders();
        applyAuth(headers, token, cred.apiKey(), cred.apiSecret());
        return headers;
    }

    /**
     * 등록 검증 — 주어진 키로 토큰이 실제로 나오는지 보고, <b>그 토큰을 캐시에 넣는다.</b>
     *
     * <p>예전에는 검증 발급을 그냥 버리고 등록 끝에 캐시를 무효화까지 해서, 키를 한 번
     * 저장할 때마다 발급이 두 번(저장 순간 + 다음 API 호출) 나갔다 = 알림톡 2건.
     * 검증에 쓴 토큰이 곧 사용할 토큰이므로 버릴 이유가 없다. 캐시에 넣으면 {@code put} 이
     * 옛 값을 덮으므로 별도 무효화도 필요 없다.
     *
     * <p>캐시 반영은 <b>커밋 이후</b>다 — 등록 트랜잭션이 롤백되면 저장되지 않은 키로 받은
     * 토큰이 캐시에 남아, 다음 호출이 옛 키 헤더에 새 키 토큰을 섞어 보내다 401 을 맞는다.
     */
    @Override
    public void verifyAndCache(Long userRowId, String apiKey, String apiSecret) {
        IssueState state = state(userRowId);
        state.lock.lock();
        try {
            BrokerToken token = issue(userRowId, apiKey, apiSecret, DeskErrorCode.SECURITIES_CREDENTIAL_INVALID);
            markIssued(state, userRowId, token, IssueReason.REGISTER);
            cacheAfterCommit(userRowId, token);
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public void invalidate(Long userRowId) {
        tokenStore.evict(broker(), userRowId);
    }

    /**
     * 401 을 받았을 때만 부른다. <b>버릴지 말지를 여기서 정한다.</b>
     *
     * <p>방금 발급한 토큰인데도 401 이면 원인은 토큰이 아니다 — 권한 미부여·차단·업스트림
     * 장애 쪽이다. 그런데도 버리고 재발급하면 실패는 그대로인 채 알림톡만 쌓인다.
     * 특히 시세 조회는 <b>종목마다 한 콜</b>이고 실패를 삼키며 다음 종목으로 넘어가므로,
     * 401 이 지속되면 폴링 한 바퀴에 (종목 수 + 1)회가 나간다 — 여기가 최악의 증폭 지점이었다.
     *
     * @return 토큰을 버렸는지. {@code false} 면 재시도해도 같은 결과이므로 호출부는 그대로 실패시킨다
     */
    @Override
    public boolean invalidateOnUnauthorized(Long userRowId) {
        IssueState state = state(userRowId);
        long issuedAt = state.lastIssuedAtMillis;
        long sinceIssue = System.currentTimeMillis() - issuedAt;
        if (issuedAt > 0 && sinceIssue < reissueCooldownMillis()) {
            log.warn("{} 401 - {}ms 전에 발급한 토큰이라 재발급하지 않는다 (userRowId={}). "
                    + "토큰이 아니라 권한·차단·업스트림 쪽을 봐야 한다", broker(), sinceIssue, userRowId);
            return false;
        }
        state.pendingReason = IssueReason.UNAUTHORIZED;
        tokenStore.evict(broker(), userRowId);
        log.info("{} 401 - 토큰을 버린다, 다음 호출에서 재발급 (userRowId={})", broker(), userRowId);
        return true;
    }

    /** 사용 가능한 크리덴셜을 복호화해 돌려준다. 미등록이면 즉시 거절한다. */
    protected ApiCredential activeCredential(Long userRowId) {
        UserSecuritiesCredential cred = credentialRepository
            .findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(userRowId, broker(), YNType.N, YNType.Y)
            .orElseThrow(() -> new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED));
        return new ApiCredential(cipher.decrypt(cred.getApiKeyEnc()), cipher.decrypt(cred.getApiSecretEnc()));
    }

    /**
     * 캐시 미스일 때의 발급. <b>사용자 단위로 직렬화한다</b> — 락을 잡고 캐시를 한 번 더 보므로
     * 동시 요청 N 개가 몰려도 발급은 1회다. 락은 사용자별이라 다른 사용자는 막지 않는다.
     *
     * <p>인스턴스 안에서만 도는 락이다. 다중 인스턴스면 인스턴스 수만큼은 나갈 수 있는데,
     * 그건 분산 락이 아니라 공유 캐시(Redis)가 이미 막고 있다 — 먼저 넣은 쪽 토큰을 나머지가
     * 캐시에서 주워 쓴다.
     */
    private String issueFor(Long userRowId, ApiCredential cred) {
        IssueState state = state(userRowId);
        state.lock.lock();
        try {
            // 락을 기다리는 사이 다른 스레드가 이미 발급했을 수 있다. 이 재확인이 빠지면
            // 콜드스타트에 동시 요청 수만큼 발급 = 알림톡이 그만큼 간다.
            Optional<String> cached = tokenStore.get(broker(), userRowId);
            if (cached.isPresent()) {
                return cached.get();
            }

            IssueReason reason = state.pendingReason == null ? IssueReason.INITIAL : state.pendingReason;
            BrokerToken token = issue(userRowId, cred.apiKey(), cred.apiSecret(), DeskErrorCode.SECURITIES_AUTH_ERROR);
            tokenStore.put(broker(), userRowId, token.accessToken(),
                Math.max(0L, token.expiresInSeconds() - EXPIRY_BUFFER_SECONDS));
            markIssued(state, userRowId, token, reason);
            return token.accessToken();
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * 발급 1회. 실패는 <b>시크릿을 지운 뒤</b> 주어진 에러코드로 바꾼다.
     *
     * <p>여기서 캐시를 건드리지 않는 게 핵심이다. 예전에는 {@code catch (RestClientException)}
     * 에서 무효화를 불렀는데, 발급이 실패했다는 것은 캐시에 넣을 게 없다는 뜻이지 <b>이미
     * 들고 있는 토큰이 틀렸다는 뜻이 아니다.</b> 동시 요청에서 한 스레드의 읽기 타임아웃이
     * 다른 스레드가 방금 저장한 멀쩡한 토큰을 지우면 그 다음 호출이 또 발급을 부른다.
     */
    private BrokerToken issue(Long userRowId, String apiKey, String apiSecret, DeskErrorCode errorCode) {
        try {
            BrokerToken token = issueToken(apiKey, apiSecret);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                log.error("{} 토큰 발급 응답이 비었다 (userRowId={})", broker(), userRowId);
                throw new ExternalServiceException(errorCode);
            }
            return token;
        } catch (RestClientException e) {
            RestClientException safe = redactSecrets(e, apiKey, apiSecret);
            log.error("{} 토큰 발급 실패 (userRowId={})", broker(), userRowId, safe);
            throw new ExternalServiceException(errorCode, safe);
        }
    }

    private void markIssued(IssueState state, Long userRowId, BrokerToken token, IssueReason reason) {
        state.lastIssuedAtMillis = System.currentTimeMillis();
        state.pendingReason = null;
        // 발급 = 사용자에게 알림톡 1건. debug 로 묻으면 알림톡이 쌓일 때 원인을 못 찾는다.
        log.info("{} 토큰 발급 (userRowId={}, 사유={}, 유효={}s)",
            broker(), userRowId, reason.label, token.expiresInSeconds());
    }

    /**
     * 발급받은 토큰을 캐시에 넣는다. 트랜잭션 안이면 <b>커밋 이후로</b> 미룬다 —
     * 등록이 롤백되면 저장되지 않은 키의 토큰만 캐시에 남는다.
     */
    private void cacheAfterCommit(Long userRowId, BrokerToken token) {
        long ttl = Math.max(0L, token.expiresInSeconds() - EXPIRY_BUFFER_SECONDS);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tokenStore.put(broker(), userRowId, token.accessToken(), ttl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tokenStore.put(broker(), userRowId, token.accessToken(), ttl);
            }
        });
    }

    private IssueState state(Long userRowId) {
        return issueStates.computeIfAbsent(userRowId, k -> new IssueState());
    }

    /**
     * 예외 메시지에서 앱키·시크릿을 지운다.
     *
     * <p>RestTemplate 은 I/O 오류 메시지에 <b>요청 URL 을 통째로</b> 싣는다
     * ({@code I/O error on POST request for "<URL>"}). 나무 토큰 발급은 스펙상 시크릿을
     * 쿼리 파라미터로 보내므로, 그 메시지가 스택트레이스째 로그에 찍히면 시크릿이 평문으로
     * 샌다. 이 레포는 같은 사고를 이미 겪었다(#253).
     *
     * <p>원본 예외를 cause 로 달지 않는다 — 달면 "Caused by:" 줄에 지우기 전 메시지가 그대로
     * 다시 나온다. 대신 원인 I/O 예외(타임아웃 등)를 그대로 물려 진단 정보는 남긴다.
     */
    private static RestClientException redactSecrets(RestClientException e, String apiKey, String apiSecret) {
        String message = mask(mask(e.getMessage(), apiSecret), apiKey);
        return new RestClientException(message == null ? e.getClass().getSimpleName() : message, e.getCause());
    }

    private static String mask(String message, String secret) {
        if (message == null || secret == null || secret.isBlank()) {
            return message;
        }
        String masked = message.replace(secret, MASK);
        // URL 에 실린 값은 인코딩된 형태로 나온다 — 원문만 지우면 그대로 남는다.
        String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);
        return encoded.equals(secret) ? masked : masked.replace(encoded, MASK);
    }
}
