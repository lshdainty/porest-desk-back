package com.porest.desk.dutchpay.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.DutchPayParticipant;
import com.porest.desk.dutchpay.type.SplitMethod;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DutchPay QueryDsl 리포 슬라이스 테스트 — 참가자 fetch join + distinct·정렬·소유권·soft-delete 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        DutchPayQueryDslRepository.class})
@ActiveProfiles("test")
class DutchPayRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private DutchPayRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private DutchPay persistDutchPay(User user, String title, LocalDate date, String... participantNames) {
        DutchPay dp = DutchPay.createDutchPay(user, null, title, null, 30_000L, "KRW",
                SplitMethod.EQUAL, date);
        for (String name : participantNames) {
            dp.addParticipant(DutchPayParticipant.create(dp, user, name, 10_000L));
        }
        return em.persist(dp); // cascade ALL → 참가자도 함께 저장
    }

    @Test
    @DisplayName("findAllByUser — 본인 더치페이만 dutchPayDate 내림차순으로 반환, 타인 제외")
    void findAllByUserOrdered() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistDutchPay(user, "6월 회식", LocalDate.of(2026, 6, 1));
        persistDutchPay(user, "7월 회식", LocalDate.of(2026, 7, 1));
        persistDutchPay(other, "남의 더치", LocalDate.of(2026, 6, 15));
        em.flush();
        em.clear();

        List<DutchPay> result = repository.findAllByUser(user.getRowId());

        assertThat(result).extracting(DutchPay::getTitle).containsExactly("7월 회식", "6월 회식");
    }

    @Test
    @DisplayName("findAllByUser — 참가자 fetch join 이어도 distinct 로 더치페이 중복 없이 1건")
    void findAllByUserDistinctWithParticipants() {
        User user = persistUser("u1");
        persistDutchPay(user, "회식", LocalDate.of(2026, 6, 1), "철수", "영희", "민수");
        em.flush();
        em.clear();

        List<DutchPay> result = repository.findAllByUser(user.getRowId());

        assertThat(result).hasSize(1); // 참가자 3명이어도 카티전 곱 없이 1건
        assertThat(result.get(0).getActiveParticipants()).hasSize(3);
    }

    @Test
    @DisplayName("findById — 참가자를 fetch join 으로 함께 로드하고, soft delete 후에는 조회되지 않는다")
    void findByIdLoadsParticipantsAndSoftDelete() {
        User user = persistUser("u1");
        DutchPay dp = persistDutchPay(user, "회식", LocalDate.of(2026, 6, 1), "철수", "영희");
        em.flush();
        em.clear();

        Optional<DutchPay> found = repository.findById(dp.getRowId());
        assertThat(found).isPresent();
        assertThat(found.get().getActiveParticipants())
                .extracting(DutchPayParticipant::getParticipantName)
                .containsExactlyInAnyOrder("철수", "영희");

        found.get().deleteDutchPay(); // isDeleted = Y (참가자도 soft-delete)
        em.flush();
        em.clear();

        assertThat(repository.findById(dp.getRowId())).isEmpty();
    }
}
