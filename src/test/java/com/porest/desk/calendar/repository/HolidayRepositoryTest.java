package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.type.HolidaySource;
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
 * Holiday QueryDsl 리포 슬라이스 테스트 — 기간 조회·soft-delete·동기화용 연도 조회 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        HolidayQueryDslRepository.class})
@ActiveProfiles("test")
class HolidayRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private HolidayRepository repository;

    private Holiday persistHoliday(String name, LocalDate date) {
        return em.persist(Holiday.create(date, name, HolidayType.PUBLIC, HolidaySource.KASI));
    }

    @Test
    @DisplayName("findByDateRange — 기간 경계를 포함해 날짜 오름차순 반환(범위 밖 제외)")
    void findByDateRange() {
        persistHoliday("start경계", LocalDate.of(2026, 6, 1));
        persistHoliday("end경계", LocalDate.of(2026, 6, 30));
        persistHoliday("범위내", LocalDate.of(2026, 6, 15));
        persistHoliday("범위전", LocalDate.of(2026, 5, 31));
        persistHoliday("범위후", LocalDate.of(2026, 7, 1));
        em.flush();
        em.clear();

        List<Holiday> result = repository.findByDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).extracting(Holiday::getHolidayName)
                .containsExactly("start경계", "범위내", "end경계");
    }

    @Test
    @DisplayName("findByDateRange — 같은 날 두 공휴일은 이름 오름차순으로 함께 반환한다")
    void findByDateRangeWithSameDate() {
        LocalDate date = LocalDate.of(2025, 5, 5);
        persistHoliday("어린이날", date);
        persistHoliday("석가탄신일", date);
        em.flush();
        em.clear();

        List<Holiday> result = repository.findByDateRange(date, date);

        assertThat(result).extracting(Holiday::getHolidayName)
                .containsExactly("석가탄신일", "어린이날");
    }

    @Test
    @DisplayName("findByDateRange — 삭제된 공휴일은 제외한다")
    void findByDateRangeExcludesDeleted() {
        Holiday deleted = persistHoliday("삭제됨", LocalDate.of(2026, 6, 10));
        persistHoliday("살아있음", LocalDate.of(2026, 6, 11));
        deleted.delete();
        em.flush();
        em.clear();

        List<Holiday> result = repository.findByDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).extracting(Holiday::getHolidayName).containsExactly("살아있음");
    }

    @Test
    @DisplayName("findByYearIncludingDeleted — 해당 연도 전체를 삭제분까지 포함해 반환한다")
    void findByYearIncludingDeleted() {
        Holiday deleted = persistHoliday("삭제됨", LocalDate.of(2026, 3, 1));
        persistHoliday("연초", LocalDate.of(2026, 1, 1));
        persistHoliday("연말", LocalDate.of(2026, 12, 31));
        persistHoliday("전년도", LocalDate.of(2025, 12, 31));
        persistHoliday("익년도", LocalDate.of(2027, 1, 1));
        deleted.delete();
        em.flush();
        em.clear();

        List<Holiday> result = repository.findByYearIncludingDeleted(2026);

        // 삭제분을 빼고 보면 (날짜, 이름) 유니크 제약에 걸려 재적재가 실패하므로 반드시 포함돼야 한다.
        assertThat(result).extracting(Holiday::getHolidayName)
                .containsExactly("연초", "삭제됨", "연말");
    }

    @Test
    @DisplayName("save — 출처를 남기고 기본 미삭제 상태로 저장한다")
    void save() {
        Holiday saved = repository.save(
                Holiday.create(LocalDate.of(2026, 7, 17), "제헌절", HolidayType.PUBLIC, HolidaySource.KASI));
        em.flush();
        em.clear();

        Holiday found = em.find(Holiday.class, saved.getRowId());
        assertThat(found.getHolidayName()).isEqualTo("제헌절");
        assertThat(found.getSource()).isEqualTo(HolidaySource.KASI);
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.isSyncManaged()).isTrue();
    }
}
