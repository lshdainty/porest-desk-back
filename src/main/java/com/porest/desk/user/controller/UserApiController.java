package com.porest.desk.user.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.user.controller.dto.UserApiDto;
import com.porest.desk.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @LoginUser UserPrincipal loginUser,
            @Valid @RequestBody UserApiDto.ChangePasswordReq request) {
        userService.changePassword(
                loginUser.getUserId(),
                request.getCurrentPassword(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
        return ApiResponse.success(null);
    }

    @PostMapping("/me/verify-password")
    public ApiResponse<Void> verifyPassword(
            @LoginUser UserPrincipal loginUser,
            @Valid @RequestBody UserApiDto.VerifyPasswordReq request) {
        userService.verifyPassword(loginUser.getUserId(), request.getPassword());
        return ApiResponse.success(null);
    }

    @GetMapping("/me/preferences")
    public ApiResponse<UserApiDto.PreferencesResponse> getPreferences(
            @LoginUser UserPrincipal loginUser) {
        Integer threshold = userService.getBudgetAlertThreshold(loginUser.getRowId());
        return ApiResponse.success(new UserApiDto.PreferencesResponse(threshold));
    }

    @PatchMapping("/me/preferences")
    public ApiResponse<UserApiDto.PreferencesResponse> updatePreferences(
            @LoginUser UserPrincipal loginUser,
            @Valid @RequestBody UserApiDto.UpdatePreferencesReq request) {
        userService.updateBudgetAlertThreshold(loginUser.getRowId(), request.getBudgetAlertThreshold());
        Integer threshold = userService.getBudgetAlertThreshold(loginUser.getRowId());
        return ApiResponse.success(new UserApiDto.PreferencesResponse(threshold));
    }
}
