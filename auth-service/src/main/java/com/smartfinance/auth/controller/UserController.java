package com.smartfinance.auth.controller;

import com.smartfinance.auth.dto.request.ChangePasswordRequest;
import com.smartfinance.auth.dto.request.UpdateProfileRequest;
import com.smartfinance.auth.dto.response.UserResponse;
import com.smartfinance.auth.service.AuthService;
import com.smartfinance.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe() {
        return ApiResponse.ok(authService.getProfile(currentUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(currentUserId(), request));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUserId(), request);
    }
}
