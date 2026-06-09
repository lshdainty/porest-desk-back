package com.porest.desk.calendar.type;

/**
 * 공유 캘린더 멤버 권한.
 * <ul>
 *   <li>{@code OWNER} : 소유자. 캘린더 생성자이며 멤버 초대·퇴출·권한변경 등 관리 권한 보유.</li>
 *   <li>{@code EDIT}  : 편집가능. 캘린더 일정 생성·수정·삭제 가능.</li>
 *   <li>{@code READ}  : 읽기전용. 일정 조회만 가능, 쓰기 불가.</li>
 * </ul>
 */
public enum CalendarRole {
    OWNER,
    EDIT,
    READ;

    /** 일정 쓰기(생성·수정·삭제) 가능 여부. 읽기전용(READ)만 불가. */
    public boolean canWrite() {
        return this != READ;
    }

    /** 멤버 초대·퇴출·권한변경 등 캘린더 관리 권한. 소유자만 가능. */
    public boolean canManageMembers() {
        return this == OWNER;
    }
}
