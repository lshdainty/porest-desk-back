package com.porest.desk.securities.client;

import com.porest.desk.securities.type.SecuritiesBroker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로세스 메모리 토큰 캐시. 단일 인스턴스·테스트용.
 *
 * <p>운영 기본값은 Redis 다({@link RedisBrokerTokenStore}) — 여기 저장한 토큰은 재시작하면
 * 사라지고 인스턴스끼리 공유되지 않아, 증권사에 재발급 요청이 인스턴스 수만큼 나간다.
 */
@Component
@ConditionalOnProperty(name = "app.securities.token-store", havingValue = "memory")
public class InMemoryBrokerTokenStore implements BrokerTokenStore {

    private record Snapshot(String token, long expiresAtMillis) {
    }

    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(SecuritiesBroker broker, Long userRowId) {
        Snapshot s = cache.get(key(broker, userRowId));
        if (s == null || System.currentTimeMillis() >= s.expiresAtMillis()) {
            return Optional.empty();
        }
        return Optional.of(s.token());
    }

    @Override
    public void put(SecuritiesBroker broker, Long userRowId, String accessToken, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        cache.put(key(broker, userRowId), new Snapshot(accessToken, expiresAt(ttlSeconds)));
    }

    @Override
    public void evict(SecuritiesBroker broker, Long userRowId) {
        cache.remove(key(broker, userRowId));
    }

    private static String key(SecuritiesBroker broker, Long userRowId) {
        return broker.name() + ':' + userRowId;
    }

    /** ttl 이 아무리 커도 오버플로로 과거가 되지 않게 막는다. */
    private static long expiresAt(long ttlSeconds) {
        long ttlMillis = ttlSeconds > (Long.MAX_VALUE / 1000L) ? Long.MAX_VALUE : ttlSeconds * 1000L;
        try {
            return Math.addExact(System.currentTimeMillis(), ttlMillis);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
