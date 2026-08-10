package com.porest.desk.common.audit;

import com.porest.core.audit.AccessLogEntry;
import com.porest.core.audit.AuditAccessPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속기록 저장 포트 구현 — core 의 {@code AuditAccessAspect} 가 이 구현으로 기록을 남긴다.
 *
 * <p>REQUIRES_NEW 로 본 트랜잭션과 분리한다. 본 작업이 롤백돼도 "접근했다" 는 사실은
 * 남아야 하기 때문이다. 저장 실패는 warn 만 남기고 삼킨다 — 감사 기록 때문에 사용자
 * 요청이 깨지면 본말이 전도된다.</p>
 *
 * @see AuditAccessPort core 포트 정의
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogPortImpl implements AuditAccessPort {

    private final AccessLogRepository accessLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AccessLogEntry entry) {
        try {
            accessLogRepository.save(AccessLog.from(entry));
        } catch (Exception e) {
            log.warn("접속기록 저장 실패: action={}, targetType={}", entry.action(), entry.targetType(), e);
        }
    }
}
