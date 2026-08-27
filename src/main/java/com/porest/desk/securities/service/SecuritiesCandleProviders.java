package com.porest.desk.securities.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 이 사용자의 캔들을 누가 그려 줄지 정한다.
 *
 * <h2>{@link SecuritiesPriceProviders} 와 규칙이 다르다</h2>
 *
 * <p>시세는 <b>기본 소스만</b> 쓴다. 자산 평가액이 어느 증권사 시세로 계산됐는지가
 * 사용자에게 의미 있는 정보라서, 서버가 몰래 다른 곳으로 갈아타면 안 되기 때문이다.
 *
 * <p>캔들은 <b>기본 소스를 우선하되, 못 주면 연결된 다른 증권사로 넘어간다.</b>
 * 차트는 시장 데이터고 계좌와 무관하다 — 삼성전자 일봉은 어디서 받아도 삼성전자 일봉이다.
 * 여기서 "기본 소스만" 을 고집하면 <b>캔들을 안 주는 증권사를 기본으로 골랐다는 이유만으로</b>
 * 차트가 통째로 사라진다. 사용자는 기본 소스를 시세 때문에 골랐지 차트를 포기하려고 고른 게 아니다.
 *
 * <h2>거절은 두 가지고, 뜻이 다르다</h2>
 *
 * <ul>
 *   <li>{@code SECURITIES_CREDENTIAL_REQUIRED}(403) — <b>연결이 하나도 없다.</b>
 *       화면은 "증권사를 연결하세요" 를 띄운다</li>
 *   <li>{@code SECURITIES_CANDLE_UNSUPPORTED}(409) — <b>연결은 있는데 캔들을 주는 곳이 없다.</b>
 *       연결하라고 말해 봐야 이미 했으므로, 다른 안내가 나가야 한다</li>
 * </ul>
 *
 * <p>둘을 한 코드로 뭉치면 이미 연결한 사용자에게 "연결하세요" 가 뜬다 — 고칠 방법이 없는
 * 안내라 사용자는 같은 일을 반복한다.
 */
@Slf4j
@Component
public class SecuritiesCandleProviders {

    private final Map<SecuritiesBroker, SecuritiesCandleProvider> byBroker =
        new EnumMap<>(SecuritiesBroker.class);
    private final SecuritiesCredentialService credentialService;

    public SecuritiesCandleProviders(List<SecuritiesCandleProvider> providers,
                                     SecuritiesCredentialService credentialService) {
        for (SecuritiesCandleProvider provider : providers) {
            byBroker.put(provider.broker(), provider);
        }
        this.credentialService = credentialService;
    }

    /**
     * 기동 로그로 <b>누가 캔들을 주는지</b>를 남긴다. 시세와 달리 전 증권사 커버리지를
     * 강제하지 않으므로(미지원이 정상이다), 여기서 터뜨리는 대신 사실만 적는다 —
     * 나중에 "왜 이 증권사는 차트가 안 뜨지" 를 로그 한 줄로 답할 수 있게.
     */
    @PostConstruct
    void logCandleCoverage() {
        List<SecuritiesBroker> unsupported = Arrays.stream(SecuritiesBroker.values())
            .filter(b -> !byBroker.containsKey(b))
            .toList();
        log.info("증권사 캔들 제공자 {}개 등록 — 지원 {}, 미지원 {}",
            byBroker.size(), byBroker.keySet(), unsupported);
    }

    /**
     * 이 사용자의 캔들 제공자. 기본 소스 → 연결된 다른 증권사 순으로 고른다.
     *
     * @throws ExternalServiceException 연결이 하나도 없을 때({@code SECURITIES_CREDENTIAL_REQUIRED})
     * @throws InvalidValueException    연결은 있으나 캔들을 주는 증권사가 없을 때
     *                                  ({@code SECURITIES_CANDLE_UNSUPPORTED})
     */
    public SecuritiesCandleProvider forUser(Long userRowId) {
        // 한 번의 조회로 전 증권사 연결·기본여부를 받는다. 증권사마다 isConnected 를 부르면
        // 증권사가 늘어날수록 쿼리가 같이 는다.
        List<BrokerConnection> connected = credentialService.getConnections(userRowId).stream()
            .filter(BrokerConnection::connected)
            .toList();
        if (connected.isEmpty()) {
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED);
        }

        SecuritiesCandleProvider primary = connected.stream()
            .filter(BrokerConnection::primary)
            .map(c -> byBroker.get(c.broker()))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (primary != null) {
            return primary;
        }

        return connected.stream()
            .map(c -> byBroker.get(c.broker()))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .map(fallback -> {
                log.debug("캔들 - 기본 소스가 캔들을 주지 않아 {} 로 대신 조회한다 (userRowId={})",
                    fallback.broker(), userRowId);
                return fallback;
            })
            .orElseThrow(() -> {
                log.warn("캔들 - 연결된 증권사가 캔들을 주지 않는다: userRowId={}, 연결={}",
                    userRowId, connected.stream().map(BrokerConnection::broker).toList());
                return new InvalidValueException(DeskErrorCode.SECURITIES_CANDLE_UNSUPPORTED);
            });
    }
}
