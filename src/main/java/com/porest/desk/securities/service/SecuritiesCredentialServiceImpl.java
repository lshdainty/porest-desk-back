package com.porest.desk.securities.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecuritiesCredentialServiceImpl implements SecuritiesCredentialService {

    private final UserSecuritiesCredentialRepository credentialRepository;
    private final AesGcmCipher cipher;
    private final BrokerTokenManagers tokenManagers;

    @Override
    @Transactional
    public void register(Long userRowId, SecuritiesBroker broker, String apiKey, String apiSecret) {
        // 1) 해당 증권사 토큰발급으로 즉시 검증 — 저장한 뒤에 틀린 키였다고 알면 늦다.
        tokenManagers.of(broker).verify(apiKey, apiSecret);

        // 2) 암호화 저장(UPSERT)
        String keyEnc = cipher.encrypt(apiKey);
        String secretEnc = cipher.encrypt(apiSecret);
        LocalDateTime now = LocalDateTime.now();

        UserSecuritiesCredential cred = credentialRepository
            .findByUserRowIdAndBroker(userRowId, broker).orElse(null);
        if (cred == null) {
            cred = credentialRepository.save(
                UserSecuritiesCredential.verified(userRowId, broker, keyEnc, secretEnc, now));
        } else {
            cred.reRegister(keyEnc, secretEnc, now);
        }

        // 3) 첫 연결이면 기본 시세 소스로 — 선택의 여지가 없으므로 묻지 않는다.
        if (activeCredentials(userRowId).stream().noneMatch(UserSecuritiesCredential::isPrimarySource)) {
            cred.markPrimary(true);
        }

        // 4) 기존 토큰 캐시 무효화 — 다른 키였을 수 있다.
        tokenManagers.of(broker).invalidate(userRowId);
        log.info("{} 크리덴셜 등록 완료 (userRowId={})", broker, userRowId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerConnection> getConnections(Long userRowId) {
        Map<SecuritiesBroker, UserSecuritiesCredential> active = new EnumMap<>(SecuritiesBroker.class);
        for (UserSecuritiesCredential c : activeCredentials(userRowId)) {
            active.put(c.getBroker(), c);
        }
        // 미연결 증권사도 내려보낸다 — 화면이 "무엇을 연결할 수 있는지" 를 서버에서 받아 그린다.
        List<BrokerConnection> connections = new ArrayList<>();
        for (SecuritiesBroker broker : SecuritiesBroker.values()) {
            UserSecuritiesCredential cred = active.get(broker);
            connections.add(cred == null ? BrokerConnection.notConnected(broker) : BrokerConnection.of(cred));
        }
        return connections;
    }

    @Override
    @Transactional
    public void disconnect(Long userRowId, SecuritiesBroker broker) {
        credentialRepository.findByUserRowIdAndBroker(userRowId, broker)
            .ifPresent(cred -> {
                boolean wasPrimary = cred.isPrimarySource();
                cred.disconnect();
                // 기본 소스를 끊으면 자산 평가가 통째로 멈춘다. 남은 연결이 있으면 승계한다 —
                // 사용자가 아무것도 안 했는데 평가액이 사라지는 것보다 낫다.
                if (wasPrimary) {
                    activeCredentials(userRowId).stream()
                        .filter(c -> c.getBroker() != broker)
                        .findFirst()
                        .ifPresent(next -> {
                            next.markPrimary(true);
                            log.info("기본 시세 소스 승계: {} → {} (userRowId={})", broker, next.getBroker(), userRowId);
                        });
                }
            });
        tokenManagers.of(broker).invalidate(userRowId);
        log.info("{} 크리덴셜 해제 (userRowId={})", broker, userRowId);
    }

    @Override
    @Transactional
    public void setPrimary(Long userRowId, SecuritiesBroker broker) {
        List<UserSecuritiesCredential> active = activeCredentials(userRowId);
        UserSecuritiesCredential target = active.stream()
            .filter(c -> c.getBroker() == broker)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED));
        // 최대 1건 Y 는 DB 제약이 아니라 여기서 지킨다.
        active.forEach(c -> c.markPrimary(c == target));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecuritiesBroker> getPrimaryBroker(Long userRowId) {
        List<UserSecuritiesCredential> active = activeCredentials(userRowId);
        return active.stream()
            .filter(UserSecuritiesCredential::isPrimarySource)
            .findFirst()
            // 지정이 없는데 연결은 있는 상태 — 마이그레이션 직후나 승계 실패에서 나올 수 있다.
            // 평가를 멈추는 대신 등록 순서가 빠른 것으로 본다.
            .or(() -> active.stream().findFirst())
            .map(UserSecuritiesCredential::getBroker);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConnected(Long userRowId, SecuritiesBroker broker) {
        return credentialRepository
            .findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(userRowId, broker, YNType.N, YNType.Y)
            .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyConnection(Long userRowId) {
        return !activeCredentials(userRowId).isEmpty();
    }

    private List<UserSecuritiesCredential> activeCredentials(Long userRowId) {
        return credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(userRowId, YNType.N, YNType.Y);
    }
}
