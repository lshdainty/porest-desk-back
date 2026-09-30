package com.porest.desk.apitoken.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * API 토큰 호출 제한 — 토큰마다 1초에 {@value #LIMIT_PER_SECOND}번.
 *
 * <p><b>왜 필요한가</b> — 토큰 뒤에는 그 사람의 증권사 키가 있다. 토스는 앱(키)마다 초당 호출 수가
 * 정해져 있고(시세 그룹 초당 5회), 그 한도를 desk 화면과 사용자 프로그램이 <b>같이</b> 쓴다.
 * 프로그램이 반복문을 잘못 돌리면 그 사람의 desk 화면까지 429 로 막힌다. 여기서 먼저 끊으면
 * 증권사까지 가지도 않는다.
 *
 * <p>키는 토큰이다 — 사용자끼리 한도를 나누지 않는다. 한 사람의 폭주가 다른 사람을 막으면 안 된다.
 *
 * <p>고정 창(1초) 카운터라 창 경계에서 순간적으로 두 배까지 지날 수 있다. 막으려는 것이 폭주이지
 * 정밀한 속도 제어가 아니라 이 정도로 충분하다. 인스턴스 메모리에 있어 재기동하면 비워진다.
 * 항목은 토큰마다 하나라 발급된 토큰 수를 넘지 않는다.
 */
@Component
public class ApiTokenRateLimiter {

    static final int LIMIT_PER_SECOND = 5;

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;

    public ApiTokenRateLimiter() {
        this(System::currentTimeMillis);
    }

    ApiTokenRateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** 이번 호출을 받아도 되는지. 받으면 센다. */
    public boolean tryAcquire(Long tokenRowId) {
        long second = clockMillis.getAsLong() / 1000;
        Window window = windows.compute(tokenRowId, (id, old) ->
            old == null || old.second() != second ? new Window(second, 1) : new Window(second, old.count() + 1));
        return window.count() <= LIMIT_PER_SECOND;
    }

    private record Window(long second, int count) {
    }
}
