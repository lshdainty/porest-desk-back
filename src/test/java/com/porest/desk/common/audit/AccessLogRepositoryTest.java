package com.porest.desk.common.audit;

import com.porest.core.audit.AccessAction;
import com.porest.core.audit.AccessLogEntry;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접속기록 레포지토리 테스트.
 *
 * <p>desk 의 기록 대상은 대량 반출(export) 이다. 본인 데이터라도 파일로 통째 빠져나간
 * 흔적은 남아야 한다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        AccessLogQueryDslRepository.class})
@ActiveProfiles("test")
class AccessLogRepositoryTest {

    @Autowired
    private AccessLogQueryDslRepository accessLogRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("반출 기록이 5항목 그대로 저장된다")
    void savesExportEntry() {
        LocalDateTime now = LocalDateTime.now();
        accessLogRepository.save(AccessLog.from(new AccessLogEntry(
                "hong", AccessAction.EXPORT, "LEDGER", null, "데이터 내보내기", "10.0.0.1", now)));
        em.flush();
        em.clear();

        List<AccessLog> found = accessLogRepository.findByActor("hong", 10);

        assertThat(found).hasSize(1);
        AccessLog saved = found.get(0);
        assertThat(saved.getActorId()).isEqualTo("hong");
        assertThat(saved.getAction()).isEqualTo("EXPORT");
        assertThat(saved.getTargetType()).isEqualTo("LEDGER");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getDetail()).isEqualTo("데이터 내보내기");
        assertThat(saved.getCreateAt()).isNotNull();
    }

    @Test
    @DisplayName("수행자별로 분리 조회된다")
    void findsByActor() {
        LocalDateTime now = LocalDateTime.now();
        accessLogRepository.save(AccessLog.from(new AccessLogEntry(
                "hong", AccessAction.EXPORT, "LEDGER", null, null, "10.0.0.1", now)));
        accessLogRepository.save(AccessLog.from(new AccessLogEntry(
                "kim", AccessAction.EXPORT, "LEDGER", null, null, "10.0.0.2", now)));
        em.flush();
        em.clear();

        assertThat(accessLogRepository.findByActor("hong", 10)).hasSize(1);
        assertThat(accessLogRepository.findByActor("kim", 10)).hasSize(1);
    }

    @Test
    @DisplayName("기간별 조회 — 반출이 몰린 시점을 찾을 수 있다")
    void findsByPeriod() {
        LocalDateTime now = LocalDateTime.now();
        accessLogRepository.save(AccessLog.from(new AccessLogEntry(
                "hong", AccessAction.EXPORT, "LEDGER", null, "과거", "10.0.0.1", now.minusDays(10))));
        accessLogRepository.save(AccessLog.from(new AccessLogEntry(
                "hong", AccessAction.EXPORT, "LEDGER", null, "최근", "10.0.0.1", now.minusHours(1))));
        em.flush();
        em.clear();

        List<AccessLog> recent = accessLogRepository.findByPeriod(now.minusDays(1), now.plusDays(1), 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getDetail()).isEqualTo("최근");
    }
}
