package com.porest.desk.securities.controller.dto;

import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public final class SecuritiesCredentialApiDto {

    private SecuritiesCredentialApiDto() {
    }

    /** 키 등록 요청. 라벨은 증권사마다 다르지만(Client ID / App Key) 자리는 둘로 같다. */
    @Schema(name = "SecuritiesCredentialRegisterRequest")
    public record RegisterRequest(String apiKey, String apiSecret) {
        /** 자동 toString 이 평문 크리덴셜을 노출하지 않게 고정한다 — 인자를 로그에 찍는 코드가 생겨도 안전. */
        @Override
        public String toString() {
            return "RegisterRequest[apiKey=***, apiSecret=***]";
        }
    }

    /**
     * 증권사 한 곳의 연결 상태 + 화면이 폼을 그리는 데 필요한 라벨.
     *
     * <p>표시명·발급처·라벨을 서버가 실어 보내므로 증권사가 늘어도 <b>앱·웹 배포 없이</b>
     * 목록에 나타난다. 클라이언트가 아는 코드면 자기 번역·아이콘을 쓰고, 모르면 여기 값으로
     * 폴백하면 된다.
     */
    public record BrokerConnectionResponse(
        String broker,
        String displayName,
        String issueUrl,
        String keyLabel,
        String secretLabel,
        boolean connected,
        boolean verified,
        LocalDateTime verifiedAt,
        boolean primary
    ) {
        public static BrokerConnectionResponse from(BrokerConnection c) {
            return new BrokerConnectionResponse(c.broker().name(), c.displayName(), c.issueUrl(),
                c.keyLabel(), c.secretLabel(), c.connected(), c.verified(), c.verifiedAt(), c.primary());
        }
    }
}
