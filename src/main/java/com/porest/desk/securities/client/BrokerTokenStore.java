package com.porest.desk.securities.client;

import com.porest.desk.securities.type.SecuritiesBroker;

import java.util.Optional;

/**
 * 사용자 × 증권사 액세스 토큰 캐시.
 *
 * <p><b>왜 인터페이스로 빼는가</b> — 나무는 토큰 수명이 24시간인데 <b>재발급을 남발하면
 * 사용자에게 보안 알림이 쌓인다.</b> 프로세스 메모리에 두면 재시작·다중 인스턴스마다
 * 전원 재발급이 나가므로 운영에서는 공유 저장소(Redis)를 써야 한다.
 * 테스트·단일 인스턴스는 메모리 구현으로 충분해 구현을 갈아끼울 수 있게 뒀다.
 */
public interface BrokerTokenStore {

    Optional<String> get(SecuritiesBroker broker, Long userRowId);

    /**
     * ttlSeconds 이하로만 보관한다. 0 이하면 저장하지 않는다(이미 만료된 토큰).
     *
     * <p>넘기는 값은 평문 토큰이다. <b>프로세스 밖으로 나가는 구현은 암호문으로 바꿔 넣는다</b>
     * — {@link RedisBrokerTokenStore} 참고. {@link #get} 이 평문으로 되돌려 주므로 호출부는
     * 저장 형식을 몰라도 된다.
     */
    void put(SecuritiesBroker broker, Long userRowId, String accessToken, long ttlSeconds);

    void evict(SecuritiesBroker broker, Long userRowId);
}
