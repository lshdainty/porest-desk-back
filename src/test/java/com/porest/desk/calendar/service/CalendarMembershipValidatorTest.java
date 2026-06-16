package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.repository.UserCalendarMemberRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.calendar.type.CalendarRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 공유 캘린더 권한 로직 회귀 방지 단위 테스트 — 멤버십·쓰기·소유자·이벤트 편집 권한.
 */
@ExtendWith(MockitoExtension.class)
class CalendarMembershipValidatorTest {

    @Mock private UserCalendarMemberRepository memberRepo;
    @Mock private UserCalendarRepository calendarRepo;

    @InjectMocks private CalendarMembershipValidator sut;

    private static final long CAL_ID = 100L;
    private static final long USER_ID = 1L;

    private UserCalendarMember member(CalendarRole role) {
        UserCalendarMember m = mock(UserCalendarMember.class);
        given(m.getPermission()).willReturn(role);
        return m;
    }

    private void calendarExists() {
        given(calendarRepo.findById(CAL_ID)).willReturn(Optional.of(mock(UserCalendar.class)));
    }

    // ---- validateMembership ----

    @Test
    @DisplayName("validateMembership — 캘린더가 없으면 NotFound")
    void membershipCalendarNotFound() {
        given(calendarRepo.findById(CAL_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.validateMembership(CAL_ID, USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("validateMembership — 멤버가 아니면 Forbidden")
    void membershipNotMember() {
        calendarExists();
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.validateMembership(CAL_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("validateMembership — 멤버면 멤버를 반환")
    void membershipSuccess() {
        calendarExists();
        UserCalendarMember m = mock(UserCalendarMember.class);
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.of(m));

        assertThat(sut.validateMembership(CAL_ID, USER_ID)).isSameAs(m);
    }

    // ---- canEditOrDelete ----

    @Test
    @DisplayName("canEditOrDelete — 본인이 만든 일정이면 권한 무관 허용")
    void canEditOwnItem() {
        UserCalendarMember m = mock(UserCalendarMember.class); // READ 라도 본인이면 허용
        assertThat(sut.canEditOrDelete(m, USER_ID, USER_ID)).isTrue();
    }

    @Test
    @DisplayName("canEditOrDelete — 남의 일정이라도 EDIT 권한이면 허용")
    void canEditOthersItemWithEdit() {
        assertThat(sut.canEditOrDelete(member(CalendarRole.EDIT), 2L, USER_ID)).isTrue();
    }

    @Test
    @DisplayName("canEditOrDelete — 남의 일정이고 READ 권한이면 불가")
    void cannotEditOthersItemWithRead() {
        assertThat(sut.canEditOrDelete(member(CalendarRole.READ), 2L, USER_ID)).isFalse();
    }

    @Test
    @DisplayName("canEditOrDelete — 생성자 불명(null·stale)이라도 EDIT 권한이면 허용(NPE 없음)")
    void canEditWithNullOwnerAndEdit() {
        assertThat(sut.canEditOrDelete(member(CalendarRole.EDIT), null, USER_ID)).isTrue();
    }

    @Test
    @DisplayName("canEditOrDelete — 생성자 불명(null)이고 READ 권한이면 불가")
    void cannotEditWithNullOwnerAndRead() {
        assertThat(sut.canEditOrDelete(member(CalendarRole.READ), null, USER_ID)).isFalse();
    }

    // ---- validateCanWrite ----

    @Test
    @DisplayName("validateCanWrite — READ 멤버는 쓰기 불가")
    void canWriteRejectsRead() {
        calendarExists();
        UserCalendarMember m = member(CalendarRole.READ);
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.validateCanWrite(CAL_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("validateCanWrite — EDIT 멤버는 쓰기 가능")
    void canWriteAllowsEdit() {
        calendarExists();
        UserCalendarMember m = member(CalendarRole.EDIT);
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.of(m));

        assertThat(sut.validateCanWrite(CAL_ID, USER_ID)).isSameAs(m);
    }

    // ---- validateOwner ----

    @Test
    @DisplayName("validateOwner — EDIT 멤버는 멤버 관리 불가")
    void ownerRejectsEdit() {
        calendarExists();
        UserCalendarMember m = member(CalendarRole.EDIT);
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.validateOwner(CAL_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("validateOwner — OWNER 는 멤버 관리 가능")
    void ownerAllowsOwner() {
        calendarExists();
        UserCalendarMember m = member(CalendarRole.OWNER);
        given(memberRepo.findByCalendarAndUser(CAL_ID, USER_ID)).willReturn(Optional.of(m));

        assertThat(sut.validateOwner(CAL_ID, USER_ID)).isSameAs(m);
    }
}
