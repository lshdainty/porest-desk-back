package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.repository.UserCalendarMemberRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 공유 캘린더 멤버십·권한 검증. group 의 GroupMembershipValidator 를 캘린더 도메인으로 이식.
 * 소유자도 user_calendar_member 에 OWNER 행으로 존재하므로 멤버 조회로 단일 판정.
 */
@Component
@RequiredArgsConstructor
public class CalendarMembershipValidator {
    private final UserCalendarMemberRepository memberRepo;
    private final UserCalendarRepository calendarRepo;

    public UserCalendarMember validateMembership(Long calendarRowId, Long userRowId) {
        calendarRepo.findById(calendarRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND));
        return memberRepo.findByCalendarAndUser(calendarRowId, userRowId)
            .orElseThrow(() -> new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED));
    }

    /**
     * 이벤트 수정·삭제 가능 여부: 이벤트 생성자 본인이거나 EDIT 이상.
     * itemOwnerRowId 는 생성자가 삭제된 stale 데이터에서 null 일 수 있으므로 null-safe 하게 처리한다
     * (생성자 불명 → 본인 일치로 보지 않고 권한(canWrite)으로만 판정).
     */
    public boolean canEditOrDelete(UserCalendarMember member, Long itemOwnerRowId, Long requestUserRowId) {
        if (itemOwnerRowId != null && itemOwnerRowId.equals(requestUserRowId)) return true;
        return member.getPermission().canWrite();
    }

    /** 쓰기 권한(일정 생성) 검증. 읽기전용(READ) 멤버는 차단. */
    public UserCalendarMember validateCanWrite(Long calendarRowId, Long userRowId) {
        UserCalendarMember member = validateMembership(calendarRowId, userRowId);
        if (!member.getPermission().canWrite()) {
            throw new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED);
        }
        return member;
    }

    /** 멤버 관리 권한(초대·퇴출·권한변경) 검증. 소유자만 허용. */
    public UserCalendarMember validateOwner(Long calendarRowId, Long userRowId) {
        UserCalendarMember member = validateMembership(calendarRowId, userRowId);
        if (!member.getPermission().canManageMembers()) {
            throw new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED);
        }
        return member;
    }

    /** 사용자가 접근 가능한(소유+공유받은) 모든 캘린더 id. */
    public List<Long> getAccessibleCalendarIds(Long userRowId) {
        return memberRepo.findCalendarIdsByUser(userRowId);
    }
}
