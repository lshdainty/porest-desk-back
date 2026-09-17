package com.porest.desk.user.service;

import com.porest.desk.user.service.dto.WithdrawalServiceDto;

/**
 * desk 이용 해지 — 이 서비스만 끊고 SSO 계정·hr 은 그대로 둔다.
 *
 * <p>계정 전체 탈퇴는 SSO 쪽 기능이다(별건). 카카오·구글·네이버가 모두 이렇게 둘로 나눠 두고
 * 있고, 여기도 SSO 에 이미 서비스별 접근 플래그({@code user_clients.is_active})가 있다.
 *
 * <p><b>되돌릴 수 없다.</b> 해지하면 같은 계정으로 desk 에 다시 들어올 수 없다.
 */
public interface WithdrawalService {

    /**
     * 해지해도 되는지, 해지하면 무엇이 사라지는지 미리 알려 준다.
     *
     * <p>화면이 확인창에 그대로 써야 하므로 <b>막는 것</b>과 <b>알리기만 하는 것</b>을 나눠 준다.
     */
    WithdrawalServiceDto.CheckResult check(Long userRowId);

    /**
     * 해지한다. 재인증 티켓이 있어야 하고, 두 번 불러도 같은 결과다(멱등).
     *
     * @param userRowId 해지할 사용자
     * @param reason    사용자가 적은 사유(선택)
     */
    void withdraw(Long userRowId, String reason);
}
