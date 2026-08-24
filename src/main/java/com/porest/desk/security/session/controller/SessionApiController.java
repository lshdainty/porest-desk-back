package com.porest.desk.security.session.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.security.session.controller.dto.SessionApiDto;
import com.porest.desk.security.session.service.SsoSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "로그인된 기기" — 본인 세션 조회·해지.
 *
 * <p>SSO 를 거치지 않는다. desk 는 로그인할 때마다 자기 세션 테이블(user_sso_session)에
 * 한 행을 남기므로 그것만으로 목록이 나온다.
 */
@RestController
@RequestMapping("/api/v1/users/me/sessions")
@RequiredArgsConstructor
public class SessionApiController {

    private final SsoSessionService ssoSessionService;

    /** 살아 있는 기기 목록. 최근 사용 순. */
    @GetMapping
    public ApiResponse<List<SessionApiDto.DeviceRes>> list(@LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(
                ssoSessionService.listDevices(loginUser.getRowId(), loginUser.getSessionId()));
    }

    /**
     * 기기 하나 로그아웃.
     *
     * <p>없거나 남의 세션이어도 성공으로 응답한다. 구분해 주면 "그 id 는 존재한다" 는 사실이
     * 새어 나간다. 사용자 입장에서도 결과는 같다 — 그 기기는 더 이상 안 붙는다.
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> revoke(@LoginUser UserPrincipal loginUser,
                                    @PathVariable String sessionId) {
        ssoSessionService.revokeOwned(loginUser.getRowId(), sessionId);
        return ApiResponse.success();
    }
}
