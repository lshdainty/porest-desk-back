package com.porest.desk.user.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 계정 연동(link) 프록시 DTO.
 *
 * <p>desk-front 가 desk 쿠키로 호출하면 desk-back 이 서비스 토큰(client_credentials)으로
 * SSO 를 대신 호출한다(BFF). userId 는 desk 쿠키에서 얻고, SSO 계약으로 relay 한다.
 */
public class OAuthLinkDto {

    /** 연동 시작 요청 — returnUrl 은 연동 완료 후 브라우저가 돌아올 desk-front 경로(선택). */
    @Getter
    @NoArgsConstructor
    public static class LinkStartReq {
        private String returnUrl;
    }

    /** 연동 시작 응답 — 브라우저가 이동해야 할 SSO 연동 시작 URL. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class StartUrlResp {
        private String startUrl;
    }

    /** 제공자별 연동 상태 — SSO providers 응답을 그대로 relay. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProviderInfoResp {
        private String type;
        private String name;
        private String authUrl;
        private boolean linked;
    }
}
