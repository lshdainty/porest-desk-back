package com.porest.desk.securities.client;

import com.porest.desk.securities.type.SecuritiesBroker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 토큰 캐시 — 운영 기본값.
 *
 * <p>인스턴스끼리 토큰을 공유하고 재시작해도 살아남는다. 나무처럼 <b>재발급이 보안 알림으로
 * 이어지는</b> 증권사에서는 이게 사실상 필수다.
 *
 * <p>TTL 은 Redis 가 관리한다 — 만료 시각을 값에 담아 직접 비교하면 인스턴스 시계가
 * 어긋났을 때 한쪽만 만료로 보는 일이 생긴다.
 */
@Component
@ConditionalOnProperty(name = "app.securities.token-store", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisBrokerTokenStore implements BrokerTokenStore {

    private static final String KEY_PREFIX = "securities:token:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> get(SecuritiesBroker broker, Long userRowId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(broker, userRowId)));
    }

    @Override
    public void put(SecuritiesBroker broker, Long userRowId, String accessToken, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(key(broker, userRowId), accessToken, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void evict(SecuritiesBroker broker, Long userRowId) {
        redisTemplate.delete(key(broker, userRowId));
    }

    private static String key(SecuritiesBroker broker, Long userRowId) {
        return KEY_PREFIX + broker.name() + ':' + userRowId;
    }
}
