package com.porest.desk.group.repository;

import com.porest.desk.group.domain.UserGroupMember;

import java.util.List;
import java.util.Optional;

public interface UserGroupMemberRepository {
    Optional<UserGroupMember> findById(Long rowId);
    Optional<UserGroupMember> findByGroupAndUser(Long groupRowId, Long userRowId);
    List<UserGroupMember> findAllByGroup(Long groupRowId);
    /**
     * 주어진 사용자가 속한 모든 그룹의 멤버를 조회.
     * DutchPay 참가자 자동완성 등 그룹 전체 멤버 풀이 필요할 때 사용.
     */
    List<UserGroupMember> findAllSiblingMembersOfUser(Long userRowId);
    UserGroupMember save(UserGroupMember member);
}
