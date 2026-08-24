package com.porest.desk.securities.client;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.type.SecuritiesBroker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 증권사별 인증 담당자를 골라 주는 진입점.
 *
 * <p><b>담당자를 늘리는 법</b> — {@code AbstractBrokerTokenManager} 를 상속한 구현에
 * {@code @Component} 를 달면 끝이다. 여기 목록을 손으로 고칠 필요가 없다.
 *
 * <p>대신 기동 시 <b>{@link SecuritiesBroker} 전 값에 구현이 있는지 확인</b>한다.
 * 자동 수집은 편하지만 빠진 걸 알아채지 못하는 게 약점이라, 증권사를 enum 에만 추가하고
 * 구현을 안 만들면 <b>첫 사용자가 누를 때가 아니라 기동할 때</b> 터지게 했다.
 */
@Slf4j
@Component
public class BrokerTokenManagers {

    private final Map<SecuritiesBroker, BrokerTokenManager> byBroker = new EnumMap<>(SecuritiesBroker.class);

    public BrokerTokenManagers(List<BrokerTokenManager> managers) {
        for (BrokerTokenManager manager : managers) {
            BrokerTokenManager previous = byBroker.put(manager.broker(), manager);
            if (previous != null) {
                throw new IllegalStateException(
                    "증권사 %s 에 인증 담당자가 둘이다: %s, %s".formatted(
                        manager.broker(), previous.getClass().getSimpleName(), manager.getClass().getSimpleName()));
            }
        }
    }

    @PostConstruct
    void verifyEveryBrokerCovered() {
        List<SecuritiesBroker> missing = java.util.Arrays.stream(SecuritiesBroker.values())
            .filter(b -> !byBroker.containsKey(b))
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("인증 담당자가 없는 증권사: " + missing);
        }
        log.info("증권사 인증 담당자 {}개 등록 — {}", byBroker.size(), byBroker.keySet());
    }

    /** 담당자를 고른다. enum 에 있는 증권사는 기동 검사가 보장하므로 여기서 못 찾을 일은 없다. */
    public BrokerTokenManager of(SecuritiesBroker broker) {
        BrokerTokenManager manager = byBroker.get(broker);
        if (manager == null) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_BROKER_UNSUPPORTED);
        }
        return manager;
    }
}
