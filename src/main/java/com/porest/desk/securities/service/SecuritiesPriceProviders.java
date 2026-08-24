package com.porest.desk.securities.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.type.SecuritiesBroker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 고른 기본 소스로 시세 제공자를 골라 준다.
 *
 * <p>어느 증권사 시세로 자산을 평가할지는 <b>사용자가 정한다</b>
 * ({@code user_securities_credential.is_primary}). 서버가 우선순위를 고정하면
 * 두 곳을 연결한 사용자가 원하는 쪽을 못 쓴다.
 *
 * <p>제공자를 늘리는 법 — {@code SecuritiesPriceProvider} 구현에 {@code @Component} 를 달면
 * 자동 등록된다. 기동 시 {@link SecuritiesBroker} 전 값에 구현이 있는지 확인한다.
 */
@Slf4j
@Component
public class SecuritiesPriceProviders {

    private final Map<SecuritiesBroker, SecuritiesPriceProvider> byBroker =
        new EnumMap<>(SecuritiesBroker.class);
    private final SecuritiesCredentialService credentialService;

    public SecuritiesPriceProviders(List<SecuritiesPriceProvider> providers,
                                    SecuritiesCredentialService credentialService) {
        for (SecuritiesPriceProvider provider : providers) {
            byBroker.put(provider.broker(), provider);
        }
        this.credentialService = credentialService;
    }

    @PostConstruct
    void verifyEveryBrokerCovered() {
        List<SecuritiesBroker> missing = Arrays.stream(SecuritiesBroker.values())
            .filter(b -> !byBroker.containsKey(b))
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("시세 제공자가 없는 증권사: " + missing);
        }
        log.info("증권사 시세 제공자 {}개 등록 — {}", byBroker.size(), byBroker.keySet());
    }

    /**
     * 이 사용자의 기본 소스 제공자. 연결이 하나도 없으면
     * {@code SECURITIES_CREDENTIAL_REQUIRED}.
     */
    public SecuritiesPriceProvider forUser(Long userRowId) {
        SecuritiesBroker broker = credentialService.getPrimaryBroker(userRowId)
            .orElseThrow(() -> new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED));
        return byBroker.get(broker);
    }
}
