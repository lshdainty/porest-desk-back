package com.porest.desk.apitoken.controller.dto;

import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.IssuedToken;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.TokenInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public final class ApiTokenApiDto {

    private ApiTokenApiDto() {
    }

    @Schema(description = "API 토큰 발급 요청")
    public record ApiTokenIssueRequest(
        @Schema(description = "어느 프로그램에 넣을 토큰인지 알아볼 이름(1~50자)", example = "보고서 차트")
        String name
    ) {
    }

    @Schema(description = "방금 발급한 API 토큰 — token 원문은 이 응답에서만 볼 수 있다")
    public record ApiTokenIssueResponse(
        Long rowId,
        String name,
        @Schema(description = "토큰 원문. 다시 조회할 수 없다", example = "pdk_…")
        String token,
        @Schema(description = "목록에서 알아보기 위한 앞부분", example = "pdk_8f3aK2xQ")
        String tokenPrefix,
        @Schema(description = "발급 시각 [UTC]")
        LocalDateTime createAt
    ) {
        public static ApiTokenIssueResponse from(IssuedToken issued) {
            return new ApiTokenIssueResponse(issued.rowId(), issued.name(), issued.token(),
                issued.tokenPrefix(), issued.createAt());
        }
    }

    @Schema(description = "API 토큰 목록의 한 줄 — 원문은 없다")
    public record ApiTokenResponse(
        Long rowId,
        String name,
        String tokenPrefix,
        @Schema(description = "발급 시각 [UTC]")
        LocalDateTime createAt,
        @Schema(description = "마지막 사용 시각 [UTC] — 분 단위로 묶어 기록한다. 안 썼으면 null")
        LocalDateTime lastUsedAt
    ) {
        public static ApiTokenResponse from(TokenInfo info) {
            return new ApiTokenResponse(info.rowId(), info.name(), info.tokenPrefix(),
                info.createAt(), info.lastUsedAt());
        }
    }
}
