package com.porest.desk.calendar.service;

import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.calendar.type.HolidaySource;
import com.porest.desk.calendar.type.HolidayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 공휴일 조회 서비스 단위 테스트.
 *
 * <p>공휴일은 자동 동기화가 채우는 조회 전용 데이터라, 서비스는 리포지토리 결과를 DTO 로 옮기기만 한다.
 * 기간 필터·정렬은 리포지토리 책임이므로 {@link com.porest.desk.calendar.repository.HolidayRepositoryTest}
 * 에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceImplTest {

    @Mock private HolidayRepository holidayRepository;

    @InjectMocks private HolidayServiceImpl sut;

    private Holiday holiday(Long rowId, LocalDate date, String name, HolidayType type) {
        Holiday h = Holiday.create(date, name, type, HolidaySource.KASI);
        ReflectionTestUtils.setField(h, "rowId", rowId);
        return h;
    }

    @Test
    @DisplayName("기간 내 공휴일을 리포지토리 순서 그대로 DTO 로 변환해 반환한다")
    void getHolidays() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        given(holidayRepository.findByDateRange(start, end)).willReturn(List.of(
                holiday(1L, LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC),
                holiday(2L, LocalDate.of(2026, 3, 2), "대체공휴일(삼일절)", HolidayType.SUBSTITUTE)));

        var result = sut.getHolidays(start, end);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rowId()).isEqualTo(1L);
        assertThat(result.get(0).holidayName()).isEqualTo("신정");
        assertThat(result.get(0).holidayType()).isEqualTo(HolidayType.PUBLIC);
        assertThat(result.get(1).holidayDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(result.get(1).holidayType()).isEqualTo(HolidayType.SUBSTITUTE);
    }

    @Test
    @DisplayName("같은 날 두 공휴일이 겹쳐도 각각 반환한다(2025-05-05 어린이날·석가탄신일)")
    void getHolidaysWithSameDate() {
        LocalDate date = LocalDate.of(2025, 5, 5);
        given(holidayRepository.findByDateRange(date, date)).willReturn(List.of(
                holiday(1L, date, "석가탄신일", HolidayType.PUBLIC),
                holiday(2L, date, "어린이날", HolidayType.PUBLIC)));

        var result = sut.getHolidays(date, date);

        assertThat(result).extracting(HolidayServiceDto.HolidayInfo::holidayName)
                .containsExactly("석가탄신일", "어린이날");
    }

    @Test
    @DisplayName("공휴일이 없으면 빈 목록을 반환한다")
    void emptyWhenNoHolidays() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());

        assertThat(sut.getHolidays(start, end)).isEmpty();
    }
}
