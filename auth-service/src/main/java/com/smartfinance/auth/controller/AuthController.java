package com.smartfinance.auth.controller;

import com.smartfinance.auth.config.JwtConfig;
import com.smartfinance.auth.dto.request.LoginRequest;
import com.smartfinance.auth.dto.request.RegisterRequest;
import com.smartfinance.auth.dto.response.AuthResponse;
import com.smartfinance.auth.exception.InvalidCredentialsException;
import com.smartfinance.auth.service.AuthService;
import com.smartfinance.auth.service.RefreshTokenService;
import com.smartfinance.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtConfig jwtConfig;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        String jti = refreshTokenService.create(authResponse.user().id());
        setRefreshCookie(response, jti);
        return ApiResponse.created(authResponse);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        String jti = refreshTokenService.create(authResponse.user().id());
        setRefreshCookie(response, jti);
        return ApiResponse.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String jti,
            HttpServletResponse response) {
        if (jti == null) {
            throw new InvalidCredentialsException();
        }
        UUID userId = refreshTokenService.getUserId(jti)
                .orElseThrow(InvalidCredentialsException::new);
        String newJti = refreshTokenService.rotate(jti)
                .orElseThrow(InvalidCredentialsException::new);
        AuthResponse authResponse = authService.refreshAuth(userId);
        setRefreshCookie(response, newJti);
        return ApiResponse.ok(authResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = "refresh_token", required = false) String jti,
            HttpServletResponse response) {
        if (jti != null) {
            refreshTokenService.invalidate(jti);
        }
        clearRefreshCookie(response);
    }

    private void setRefreshCookie(HttpServletResponse response, String jti) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", jti)
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .maxAge(jwtConfig.refreshTokenExpiration())
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
