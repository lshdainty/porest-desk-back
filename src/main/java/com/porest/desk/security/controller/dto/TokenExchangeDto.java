package com.porest.desk.security.controller.dto;

public class TokenExchangeDto {
    public record Request(String ssoToken) {}
    public record Response(String accessToken, String userId, String userName, String userEmail) {}
    public record CheckResponse(Long rowId, String userId, String userName, String userEmail, String timezone) {}
}
