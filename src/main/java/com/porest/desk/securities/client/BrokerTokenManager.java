package com.porest.desk.securities.client;

import com.porest.desk.securities.type.SecuritiesBroker;
import org.springframework.http.HttpHeaders;

/**
 * 증권사 하나의 인증을 맡는다 — 토큰 발급·캐시·요청 헤더 구성.
 *
 * <p><b>왜 증권사마다 구현을 두는가</b> — 프로토콜이 실제로 다르다. 토스는 폼 바디로
 * {@code client_id}/{@code client_secret} 을 보내고 {@code Bearer} 헤더 하나면 되는데,
 * 나무는 쿼리 파라미터로 {@code appkey}/{@code appsecretkey}/{@code scope=oob} 를 보내고
 * <b>매 호출마다</b> {@code x-client-id}/{@code x-client-secret} 을 Bearer 와 함께 요구한다.
 * 하나의 발급기에 분기를 넣으면 한쪽을 고칠 때 다른 쪽이 깨진다.
 *
 * <p><b>확장 방법</b> — {@code AbstractBrokerTokenManager} 를 상속해 {@code issueToken} 하나만
 * 구현하면 된다. 캐시·복호화·만료 버퍼·예외 변환은 부모가 처리한다. 인증 헤더가 Bearer 하나로
 * 끝나지 않는 증권사만 {@code applyAuth} 를 추가로 재정의한다(나무가 그 경우다).
 * {@code @Component} 로 올리면 {@link BrokerTokenManagers} 가 자동으로 주워 간다.
 */
public interface BrokerTokenManager {

    SecuritiesBroker broker();

    /** 사용자 본인 키로 발급한 유효 토큰. 미등록 시 {@code SECURITIES_CREDENTIAL_REQUIRED}. */
    String getAccessToken(Long userRowId);

    /**
     * 이 증권사 API 를 호출할 때 붙일 인증 헤더 일습.
     *
     * <p>토큰만으로 끝나지 않는 증권사가 있어 헤더 구성을 여기서 책임진다 — 나무는 평문
     * 키/시크릿을 매 호출에 실어야 한다. 복호화한 평문이 이 메서드 밖으로 새지 않게 한다.
     */
    HttpHeaders authHeaders(Long userRowId);

    /**
     * 등록 검증 — 주어진 키로 토큰이 실제로 나오는지 보고 <b>그 토큰을 캐시에 넣는다.</b>
     * 실패 시 {@code SECURITIES_CREDENTIAL_INVALID}. 크리덴셜 저장 전에 호출한다.
     *
     * <p><b>왜 검증과 캐시가 한 메서드인가</b> — 나무는 발급 한 번이 사용자 알림톡 한 건이다.
     * 검증만 하고 토큰을 버리면 키를 한 번 저장할 때마다 발급이 두 번(검증 + 다음 API 호출)
     * 나간다. 검증에 쓴 토큰이 곧 사용할 토큰이므로 그대로 캐시에 넣어 한 번으로 줄인다.
     * 캐시 반영은 등록 트랜잭션 커밋 이후다 — 롤백되면 저장 안 된 키의 토큰만 남는다.
     */
    void verifyAndCache(Long userRowId, String apiKey, String apiSecret);

    /** 캐시된 토큰을 조건 없이 버린다. 연결 해제처럼 <b>우리가 확실히 아는</b> 자리에만 쓴다. */
    void invalidate(Long userRowId);

    /**
     * HTTP 401 을 받았을 때만 부른다 — 증권사가 "이 토큰은 무효다" 라고 말한 유일한 신호다.
     *
     * <p>통신 오류·타임아웃·한도 초과(429)·업무 오류({@code rsp_cd})에는 <b>부르지 마라.</b>
     * 토큰 문제가 아닌데 버리면 실패는 그대로인 채 재발급 알림톡만 쌓인다.
     *
     * <p>방금 발급한 토큰으로도 401 이 나는 상황이면 구현이 재발급을 거절할 수 있다.
     *
     * @return 토큰을 버렸는지. {@code false} 면 재시도해도 결과가 같으니 그대로 실패시킨다
     */
    boolean invalidateOnUnauthorized(Long userRowId);
}
