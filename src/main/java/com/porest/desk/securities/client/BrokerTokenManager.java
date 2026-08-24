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
     * 등록 검증 — 주어진 키로 토큰이 실제로 나오는지 본다.
     * 실패 시 {@code SECURITIES_CREDENTIAL_INVALID}. 저장 전에 호출한다.
     */
    void verify(String apiKey, String apiSecret);

    void invalidate(Long userRowId);
}
