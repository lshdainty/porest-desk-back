package com.porest.desk.calendar.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.UserCalendarMemberRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.calendar.type.CalendarRole;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 사용자 캘린더(공유) 서비스 회귀 방지 단위 테스트 —
 * 기본 캘린더 삭제 금지, 소유권, 소유자 제거/OWNER 권한변경 금지, 중복 가입 금지.
 */
@ExtendWith(MockitoExtension.class)
class UserCalendarServiceImplTest {

    @Mock private UserCalendarRepository userCalendarRepository;
    @Mock private UserCalendarMemberRepository memberRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private CalendarMembershipValidator membershipValidator;

    @InjectMocks private UserCalendarServiceImpl sut;

    private static final long CAL_ID = 100L;
    private static final long MEMBER_ID = 7L;
    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    @Test
    @DisplayName("deleteCalendar — 기본 캘린더는 삭제 불가")
    void deleteRejectsDefaultCalendar() {
        UserCalendar calendar = mock(UserCalendar.class);
        given(calendar.getUser()).willReturn(user(USER_ID));
        given(calendar.getIsDefault()).willReturn(YNType.Y);
        given(userCalendarRepository.findById(CAL_ID)).willReturn(Optional.of(calendar));

        assertThatThrownBy(() -> sut.deleteCalendar(CAL_ID, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("deleteCalendar — 남의 캘린더는 삭제 불가")
    void deleteRejectsOthers() {
        UserCalendar calendar = mock(UserCalendar.class);
        given(calendar.getUser()).willReturn(user(999L));
        given(userCalendarRepository.findById(CAL_ID)).willReturn(Optional.of(calendar));

        assertThatThrownBy(() -> sut.deleteCalendar(CAL_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("removeMember — 소유자(OWNER)는 제거 불가")
    void removeMemberRejectsOwner() {
        UserCalendarMember member = mock(UserCalendarMember.class);
        given(member.getPermission()).willReturn(CalendarRole.OWNER);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> sut.removeMember(CAL_ID, MEMBER_ID, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("changeMemberRole — OWNER 권한으로는 변경 불가")
    void changeRoleRejectsOwnerPermission() {
        assertThatThrownBy(() -> sut.changeMemberRole(CAL_ID, MEMBER_ID, CalendarRole.OWNER, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("joinByInviteCode — 이미 멤버면 중복 가입 불가")
    void joinRejectsAlreadyMember() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        UserCalendar calendar = mock(UserCalendar.class);
        given(calendar.getRowId()).willReturn(50L);
        given(userCalendarRepository.findByInviteCode("CODE")).willReturn(Optional.of(calendar));
        UserCalendarMember existing = mock(UserCalendarMember.class);
        given(memberRepository.findByCalendarAndUser(50L, USER_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> sut.joinByInviteCode(USER_ID, "CODE"))
                .isInstanceOf(InvalidValueException.class);
    }
}
