package com.porest.desk.securities.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.securities.service.SecuritiesCredentialService;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 구버전 클라이언트 호환 — 옛 토스 전용 크리덴셜 API.
 *
 * <p><b>왜 남기나</b> — 앱이 스토어를 안 써서 자동 업데이트가 없다. 구버전 앱이 계속 도는데
 * 이 경로가 사라지면 토스 키를 이미 등록한 사용자도 증권 화면이 "연결하세요" 로 되돌아간다
 * (상태 조회가 404 → 에러 → connected=false). 강제 업데이트로 막을 만큼 위험한 변경이
 * 아니라서(값이 조용히 틀리는 게 아니라 기능이 눈에 띄게 안 되는 쪽이다) 경로를 한 시즌 더 둔다.
 *
 * <p>새 코드는 {@link UserSecuritiesCredentialApiController}
 * ({@code /api/v1/users/me/securities-credentials})를 쓴다. 여기서는 TOSS 로 고정 위임만 한다.
 *
 * <p>지울 시점 — {@code min_build.json} 의 하한이 이 경로를 안 쓰는 빌드 이상으로 올라간 뒤.
 *
 * @deprecated 구버전 앱 전용. 새 클라이언트는 securities-credentials 를 쓴다.
 */
@Deprecated(since = "2026-08-24")
@RestController
@RequestMapping("/api/v1/users/me/toss-credential")
@RequiredArgsConstructor
public class LegacyTossCredentialApiController {

    private final SecuritiesCredentialService credentialService;

    @PostMapping
    public ApiResponse<Void> register(
            @LoginUser UserPrincipal loginUser,
            @RequestBody RegisterRequest request) {
        credentialService.register(loginUser.getRowId(), SecuritiesBroker.TOSS,
            request.clientId(), request.clientSecret());
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<CredentialStatusResponse> getStatus(@LoginUser UserPrincipal loginUser) {
        BrokerConnection toss = credentialService.getConnections(loginUser.getRowId()).stream()
            .filter(c -> c.broker() == SecuritiesBroker.TOSS)
            .findFirst()
            .orElse(BrokerConnection.notConnected(SecuritiesBroker.TOSS));
        return ApiResponse.success(
            new CredentialStatusResponse(toss.connected(), toss.verified(), toss.verifiedAt()));
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect(@LoginUser UserPrincipal loginUser) {
        credentialService.disconnect(loginUser.getRowId(), SecuritiesBroker.TOSS);
        return ApiResponse.success();
    }

    /**
     * 옛 요청 본문. 필드명이 새 API(apiKey/apiSecret)와 다르므로 그대로 둔다.
     *
     * <p>{@code @Schema(name)} 이 붙은 이유 — springdoc 은 스키마를 단순 클래스명으로 등록해서
     * 새 API 의 {@code RegisterRequest} 와 한 칸을 두고 다퉜고, 여기가 이겨 <b>나무 크리덴셜
     * 등록이 {@code clientId/clientSecret} 으로 문서화됐다.</b> 스펙대로 부르면 서버에서
     * apiKey/apiSecret 이 null 이 되어 {@code verify(null, null)} 이 나간다.
     * {@code OpenApiSchemaNameTest} 가 이름 충돌을 전수로 지킨다.
     */
    @Schema(name = "TossCredentialRegisterRequest")
    public record RegisterRequest(String clientId, String clientSecret) {
    }

    public record CredentialStatusResponse(boolean connected, boolean verified, LocalDateTime verifiedAt) {
    }
}
