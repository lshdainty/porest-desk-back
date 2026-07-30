package com.porest.desk.calendar.service;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.client.HolidayProvider;
import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.exception.HolidayProviderException;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import com.porest.desk.calendar.type.HolidaySource;
import com.porest.desk.calendar.type.HolidayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 연도 동기화 diff 로직 테스트.
 *
 * <p>전량 삭제 후 재적재하면 변경이 없는 날에도 수정 이력이 매일 갱신되므로, 실제로 달라진 행만
 * 손대야 한다. 또한 소스 응답이 비었을 때 기존 데이터를 지워 버리면 캘린더가 통째로 비므로
 * "빈 응답 = 유지"가 보장돼야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayYearSynchronizerTest {

    @Mock private HolidayProvider kasiProvider;
    @Mock private HolidayProvider fallbackProvider;
    @Mock private HolidayRepository holidayRepository;

    private HolidayYearSynchronizer sut(HolidayProvider... providers) {
        return new HolidayYearSynchronizer(List.of(providers), holidayRepository);
    }

    private Holiday existing(LocalDate date, String name, HolidayType type, HolidaySource source) {
        return Holiday.create(date, name, type, source);
    }

    private ExternalHoliday external(LocalDate date, String name, HolidayType type) {
        return new ExternalHoliday(date, name, type);
    }

    @Test
    @DisplayName("DB 에 없는 공휴일은 소스 출처와 함께 새로 적재한다")
    void insertsNewHolidays() {
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 7, 17), "제헌절", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of());

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getHolidayName()).isEqualTo("제헌절");
        assertThat(captor.getValue().getSource()).isEqualTo(HolidaySource.KASI);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.source()).isEqualTo(HolidaySource.KASI);
    }

    @Test
    @DisplayName("이미 같은 내용이면 아무것도 건드리지 않는다")
    void leavesUnchangedHolidaysAlone() {
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(
                existing(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC, HolidaySource.KASI)));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        verify(holidayRepository, never()).save(any());
        assertThat(result.hasChanges()).isFalse();
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("유형이 달라졌으면 갱신한다")
    void updatesChangedType() {
        Holiday stored = existing(LocalDate.of(2026, 5, 1), "노동절", HolidayType.SUBSTITUTE, HolidaySource.KASI);
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 5, 1), "노동절", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(stored));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        assertThat(stored.getHolidayType()).isEqualTo(HolidayType.PUBLIC);
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("소스에서 사라진 공휴일은 삭제 처리한다")
    void removesDisappearedHolidays() {
        Holiday stale = existing(LocalDate.of(2026, 5, 1), "근로자의 날", HolidayType.SUBSTITUTE, HolidaySource.KASI);
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 5, 1), "노동절", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(stale));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        assertThat(stale.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1); // '노동절'은 새 이름이라 신규 적재
    }

    @Test
    @DisplayName("수기 등록분과 사내 휴무는 소스에 없어도 지우지 않는다")
    void keepsManualAndCustomHolidays() {
        Holiday manual = existing(LocalDate.of(2026, 4, 10), "창립기념일", HolidayType.CUSTOM, HolidaySource.MANUAL);
        Holiday custom = existing(LocalDate.of(2026, 5, 1), "근로자의 날", HolidayType.CUSTOM, HolidaySource.KASI);
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(manual, custom));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        assertThat(manual.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(custom.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(result.removed()).isZero();
    }

    @Test
    @DisplayName("사용자가 지운 공휴일은 되살리지 않는다")
    void doesNotResurrectDeletedHolidays() {
        Holiday deleted = existing(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC, HolidaySource.KASI);
        deleted.delete();
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(deleted));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        // 유니크 제약(날짜+이름) 때문에 재적재하면 실패한다. 삭제 상태 그대로 둔다.
        verify(holidayRepository, never()).save(any());
        assertThat(deleted.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("소스 응답이 비면 기존 데이터를 지우지 않는다")
    void keepsExistingWhenSourceEmpty() {
        Holiday stored = existing(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC, HolidaySource.KASI);
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2026)).willReturn(List.of());
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of(stored));

        HolidaySyncResult result = sut(kasiProvider).sync(2026);

        assertThat(stored.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(result.isFailed()).isFalse();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("1순위 소스가 실패하면 폴백으로 넘어가고 출처도 폴백으로 남는다")
    void fallsBackWhenPrimaryFails() {
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        willThrow(new HolidayProviderException("인증키 오류")).given(kasiProvider).fetch(2026);
        given(fallbackProvider.source()).willReturn(HolidaySource.HOLIDAYS_KR);
        given(fallbackProvider.fetch(2026)).willReturn(List.of(
                external(LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2026)).willReturn(List.of());

        HolidaySyncResult result = sut(kasiProvider, fallbackProvider).sync(2026);

        assertThat(result.source()).isEqualTo(HolidaySource.HOLIDAYS_KR);
        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 소스가 실패하면 실패 결과를 돌려주고 DB 는 건드리지 않는다")
    void returnsFailedWhenAllProvidersFail() {
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        willThrow(new HolidayProviderException("실패")).given(kasiProvider).fetch(2026);
        given(fallbackProvider.source()).willReturn(HolidaySource.HOLIDAYS_KR);
        willThrow(new HolidayProviderException("실패")).given(fallbackProvider).fetch(2026);

        HolidaySyncResult result = sut(kasiProvider, fallbackProvider).sync(2026);

        assertThat(result.isFailed()).isTrue();
        verify(holidayRepository, never()).findByYearIncludingDeleted(any(Integer.class));
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 날 두 공휴일이 겹쳐도 이름으로 구분해 각각 적재한다")
    void handlesTwoHolidaysOnSameDate() {
        LocalDate date = LocalDate.of(2025, 5, 5);
        given(kasiProvider.source()).willReturn(HolidaySource.KASI);
        given(kasiProvider.fetch(2025)).willReturn(List.of(
                external(date, "어린이날", HolidayType.PUBLIC),
                external(date, "석가탄신일", HolidayType.PUBLIC)));
        given(holidayRepository.findByYearIncludingDeleted(2025)).willReturn(List.of(
                existing(date, "어린이날", HolidayType.PUBLIC, HolidaySource.KASI)));

        HolidaySyncResult result = sut(kasiProvider).sync(2025);

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(captor.capture());
        assertThat(captor.getValue().getHolidayName()).isEqualTo("석가탄신일");
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.removed()).isZero();
    }
}
