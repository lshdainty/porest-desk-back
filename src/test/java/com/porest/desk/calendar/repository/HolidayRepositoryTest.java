package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.type.HolidayType;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holiday QueryDsl 리포 슬라이스 테스트 — 기간 조회(비반복만)·반복 공휴일 분리·soft-delete 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        HolidayQueryDslRepository.class})
@ActiveProfiles("test")
class HolidayRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private HolidayRepository repository;

    private Holiday persistHoliday(String name, LocalDate date, YNType recurring) {
        return em.persist(Holiday.create(date, name, HolidayType.PUBLIC, recurring));
    }

    @Test
    @DisplayName("findByDateRange — 비반복 공휴일만 기간 경계 포함해 날짜 오름차순 반환(반복·범위밖 제외)")
    void findByDateRange() {
        persistHoliday("start경계", LocalDate.of(2026, 6, 1), YNType.N);  // start 경계 포함
        persistHoliday("end경계", LocalDate.of(2026, 6, 30), YNType.N);   // end 경계 포함
        persistHoliday("범위내", LocalDate.of(2026, 6, 15), YNType.N);
        persistHoliday("범위전", LocalDate.of(2026, 5, 31), YNType.N);    // 제외
        persistHoliday("범위후", LocalDate.of(2026, 7, 1), YNType.N);     // 제외
        persistHoliday("반복", LocalDate.of(2026, 6, 10), YNType.Y);      // 반복 → 제외
        em.flush();
        em.clear();

        List<Holiday> result = repository.findByDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).extracting(Holiday::getHolidayName)
                .containsExactly("start경계", "범위내", "end경계"); // holidayDate asc
    }

    @Test
    @DisplayName("findAllRecurring — 반복(isRecurring=Y) 공휴일만 날짜 오름차순 반환, 비반복 제외")
    void findAllRecurring() {
        persistHoliday("설날", LocalDate.of(2026, 2, 17), YNType.Y);
        persistHoliday("추석", LocalDate.of(2026, 9, 25), YNType.Y);
        persistHoliday("비반복", LocalDate.of(2026, 6, 6), YNType.N); // 제외
        em.flush();
        em.clear();

        List<Holiday> result = repository.findAllRecurring();

        assertThat(result).extracting(Holiday::getHolidayName).containsExactly("설날", "추석");
    }

    @Test
    @DisplayName("soft delete 된 공휴일은 findByDateRange / findById 에서 제외된다")
    void softDeleteExcluded() {
        Holiday live = persistHoliday("현충일", LocalDate.of(2026, 6, 6), YNType.N);
        Holiday removed = persistHoliday("삭제될날", LocalDate.of(2026, 6, 7), YNType.N);
        em.flush();

        removed.delete(); // isDeleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findByDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .extracting(Holiday::getHolidayName).containsExactly("현충일");
        assertThat(repository.findById(removed.getRowId())).isEmpty();
        assertThat(repository.findById(live.getRowId())).isPresent();
    }
}
