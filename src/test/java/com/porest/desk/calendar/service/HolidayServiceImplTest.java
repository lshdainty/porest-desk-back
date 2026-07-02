package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.calendar.type.HolidayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 공휴일 서비스 조회/계산 로직 회귀 방지 단위 테스트.
 *
 * <p>repository 는 모두 mock — DB·컨텍스트 없이 {@link HolidayServiceImpl} 의
 * 로직만 검증한다. 핵심은 {@code getHolidays} 의 반복 공휴일 가상 엔트리 확장
 * (연도 범위 전개, (월·일·명) 중복 제거, 범위 경계 포함/제외, 윤일(2/29) 비윤년 건너뜀,
 * 반복/비반복 병합 후 날짜 정렬)이다.
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceImplTest {

    @Mock private HolidayRepository holidayRepository;

    @InjectMocks private HolidayServiceImpl sut;

    /** rowId 는 DB 생성값이므로 리플렉션으로 주입한 Holiday 를 만든다. */
    private Holiday holiday(Long rowId, LocalDate date, String name, HolidayType type, YNType recurring) {
        Holiday h = Holiday.create(date, name, type, recurring);
        ReflectionTestUtils.setField(h, "rowId", rowId);
        return h;
    }

    @Nested
    @DisplayName("createHoliday")
    class CreateHoliday {

        @Test
        @DisplayName("공휴일을 생성하고 저장한다")
        void create() {
            var command = new HolidayServiceDto.CreateCommand(
                    LocalDate.of(2024, 3, 1), "삼일절", HolidayType.PUBLIC, YNType.N);

            var info = sut.createHoliday(command);

            assertThat(info.holidayDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(info.holidayName()).isEqualTo("삼일절");
            assertThat(info.holidayType()).isEqualTo(HolidayType.PUBLIC);
            assertThat(info.isRecurring()).isEqualTo(YNType.N);
            verify(holidayRepository).save(any(Holiday.class));
        }

        @Test
        @DisplayName("반복 여부(isRecurring)가 null 이면 N 으로 기본 설정된다")
        void createDefaultsRecurringToN() {
            var command = new HolidayServiceDto.CreateCommand(
                    LocalDate.of(2024, 1, 1), "신정", HolidayType.PUBLIC, null);

            var info = sut.createHoliday(command);

            assertThat(info.isRecurring()).isEqualTo(YNType.N);
            verify(holidayRepository).save(any(Holiday.class));
        }
    }

    @Nested
    @DisplayName("getHolidays")
    class GetHolidays {

        @Test
        @DisplayName("비반복 공휴일만 있으면 그대로 반환하되 날짜순으로 정렬한다")
        void nonRecurringOnlySorted() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of(
                    holiday(1L, LocalDate.of(2024, 5, 5), "어린이날", HolidayType.PUBLIC, YNType.N),
                    holiday(2L, LocalDate.of(2024, 3, 1), "삼일절", HolidayType.PUBLIC, YNType.N)));
            given(holidayRepository.findAllRecurring()).willReturn(List.of());

            var result = sut.getHolidays(start, end);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).holidayDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(result.get(0).holidayName()).isEqualTo("삼일절");
            assertThat(result.get(1).holidayDate()).isEqualTo(LocalDate.of(2024, 5, 5));
        }

        @Test
        @DisplayName("반복 공휴일은 조회 범위 내 각 연도에 가상 엔트리로 전개한다(시작일 포함)")
        void recurringExpandsAcrossYears() {
            LocalDate start = LocalDate.of(2023, 1, 1);
            LocalDate end = LocalDate.of(2025, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 1, 1), "신정", HolidayType.PUBLIC, YNType.Y)));

            var result = sut.getHolidays(start, end);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(HolidayServiceDto.HolidayInfo::holidayDate)
                    .containsExactly(
                            LocalDate.of(2023, 1, 1),
                            LocalDate.of(2024, 1, 1),
                            LocalDate.of(2025, 1, 1));
            assertThat(result).allSatisfy(info -> {
                assertThat(info.holidayName()).isEqualTo("신정");
                assertThat(info.rowId()).isEqualTo(10L); // 가상 엔트리는 원본 rowId 를 유지
                assertThat(info.isRecurring()).isEqualTo(YNType.Y);
            });
        }

        @Test
        @DisplayName("동일한 (월·일·공휴일명) 반복 공휴일이 여러 연도에 있으면 한 번만 전개한다(중복 제거)")
        void recurringDedupBySameMonthDayName() {
            LocalDate start = LocalDate.of(2023, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 1, 1), "신정", HolidayType.PUBLIC, YNType.Y),
                    holiday(11L, LocalDate.of(2021, 1, 1), "신정", HolidayType.PUBLIC, YNType.Y)));

            var result = sut.getHolidays(start, end);

            // 4개(2연도×2원본)가 아니라 2개(2연도×중복제거 1건)
            assertThat(result).hasSize(2);
            assertThat(result).extracting(HolidayServiceDto.HolidayInfo::holidayDate)
                    .containsExactly(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
            assertThat(result).allSatisfy(info -> assertThat(info.rowId()).isEqualTo(10L));
        }

        @Test
        @DisplayName("반복 공휴일의 해당 연도 날짜가 조회 범위를 벗어나면 제외한다(종료일 이후)")
        void recurringOutOfRangeExcluded() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 6, 30);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 12, 25), "성탄절", HolidayType.PUBLIC, YNType.Y)));

            var result = sut.getHolidays(start, end);

            // 2024-12-25 는 종료일(2024-06-30) 이후라 제외
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("2/29 반복 공휴일은 윤년에만 전개하고 평년은 건너뛴다")
        void recurringSkipsFeb29InNonLeapYears() {
            LocalDate start = LocalDate.of(2021, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 2, 29), "윤일", HolidayType.CUSTOM, YNType.Y)));

            var result = sut.getHolidays(start, end);

            // 2021~2023 평년은 2/29 없음 → 건너뜀, 2024 윤년만 전개
            assertThat(result).hasSize(1);
            assertThat(result.get(0).holidayDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("반복·비반복 공휴일을 병합한 뒤 날짜순으로 정렬한다")
        void mergesRecurringAndNonRecurringSorted() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of(
                    holiday(1L, LocalDate.of(2024, 5, 5), "어린이날", HolidayType.PUBLIC, YNType.N)));
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 1, 1), "신정", HolidayType.PUBLIC, YNType.Y)));

            var result = sut.getHolidays(start, end);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).holidayDate()).isEqualTo(LocalDate.of(2024, 1, 1)); // 반복 전개(신정)
            assertThat(result.get(0).holidayName()).isEqualTo("신정");
            assertThat(result.get(1).holidayDate()).isEqualTo(LocalDate.of(2024, 5, 5)); // 비반복(어린이날)
            assertThat(result.get(1).holidayName()).isEqualTo("어린이날");
        }

        @Test
        @DisplayName("공휴일이 없으면 빈 목록을 반환한다")
        void emptyWhenNoHolidays() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of());

            var result = sut.getHolidays(start, end);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("연말연시 경계: 시작 연도 발생분은 범위 밖이라 제외하고 종료일(포함)에 해당하는 발생분만 전개한다")
        void yearBoundaryInclusiveEnd() {
            LocalDate start = LocalDate.of(2023, 12, 31);
            LocalDate end = LocalDate.of(2024, 1, 1);
            given(holidayRepository.findByDateRange(start, end)).willReturn(List.of());
            given(holidayRepository.findAllRecurring()).willReturn(List.of(
                    holiday(10L, LocalDate.of(2020, 1, 1), "신정", HolidayType.PUBLIC, YNType.Y)));

            var result = sut.getHolidays(start, end);

            // 2023-01-01 은 시작일(2023-12-31) 이전이라 제외, 2024-01-01 은 종료일과 같아 포함
            assertThat(result).hasSize(1);
            assertThat(result.get(0).holidayDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }
    }

    @Nested
    @DisplayName("updateHoliday")
    class UpdateHoliday {

        @Test
        @DisplayName("공휴일 정보를 수정하고 수정 결과를 반환한다")
        void update() {
            Holiday existing = holiday(5L, LocalDate.of(2024, 1, 1), "신정", HolidayType.PUBLIC, YNType.N);
            given(holidayRepository.findById(5L)).willReturn(Optional.of(existing));

            var command = new HolidayServiceDto.UpdateCommand(
                    LocalDate.of(2024, 1, 2), "대체공휴일", HolidayType.SUBSTITUTE, YNType.Y);

            var info = sut.updateHoliday(5L, command);

            assertThat(info.rowId()).isEqualTo(5L);
            assertThat(info.holidayDate()).isEqualTo(LocalDate.of(2024, 1, 2));
            assertThat(info.holidayName()).isEqualTo("대체공휴일");
            assertThat(info.holidayType()).isEqualTo(HolidayType.SUBSTITUTE);
            assertThat(info.isRecurring()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("존재하지 않는 공휴일 수정은 EntityNotFoundException")
        void updateNotFound() {
            given(holidayRepository.findById(99L)).willReturn(Optional.empty());

            var command = new HolidayServiceDto.UpdateCommand(
                    LocalDate.of(2024, 1, 2), "대체공휴일", HolidayType.SUBSTITUTE, YNType.Y);

            assertThatThrownBy(() -> sut.updateHoliday(99L, command))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteHoliday")
    class DeleteHoliday {

        @Test
        @DisplayName("공휴일을 소프트 삭제한다(isDeleted=Y)")
        void softDelete() {
            Holiday existing = holiday(5L, LocalDate.of(2024, 1, 1), "신정", HolidayType.PUBLIC, YNType.N);
            given(holidayRepository.findById(5L)).willReturn(Optional.of(existing));

            sut.deleteHoliday(5L);

            assertThat(existing.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("존재하지 않는 공휴일 삭제는 EntityNotFoundException")
        void deleteNotFound() {
            given(holidayRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deleteHoliday(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
