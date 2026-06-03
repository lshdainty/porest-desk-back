package com.porest.desk.group.type;

/**
 * 공유 캘린더(그룹) 멤버 권한.
 * <ul>
 *   <li>{@code OWNER} : 소유자. 캘린더 생성자이며 멤버 초대·퇴출·권한변경 등 관리 권한 보유.</li>
 *   <li>{@code EDIT}  : 편집가능. 그룹 일정/지출 생성·수정·삭제 가능. (구 ADMIN/MEMBER 통합)</li>
 *   <li>{@code READ}  : 읽기전용. 그룹 콘텐츠 조회만 가능, 쓰기 불가.</li>
 * </ul>
 */
public enum GroupRole {
    OWNER,
    EDIT,
    READ;

    /** 일정/지출 등 그룹 콘텐츠 쓰기(생성·수정·삭제) 가능 여부. 읽기전용(READ)만 불가. */
    public boolean canWrite() {
        return this != READ;
    }

    /** 멤버 초대·퇴출·권한변경 등 그룹 관리 권한. 소유자만 가능. */
    public boolean canManageMembers() {
        return this == OWNER;
    }
}
