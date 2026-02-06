package com.porest.desk.security.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.controller.dto.TokenExchangeDto;
import com.porest.desk.security.service.TokenExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class TokenExchangeController {
    private final TokenExchangeService tokenExchangeService;

    @PostMapping("/exchange")
    public ApiResponse<TokenExchangeDto.Response> exchangeToken(@RequestBody TokenExchangeDto.Request request) {
        TokenExchangeDto.Response response = tokenExchangeService.exchangeToken(request.ssoToken());
        return ApiResponse.success(response);
    }
}
