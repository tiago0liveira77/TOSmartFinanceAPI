package com.smartfinance.auth.service;

import com.smartfinance.auth.config.JwtConfig;
import com.smartfinance.auth.dto.request.ChangePasswordRequest;
import com.smartfinance.auth.dto.request.LoginRequest;
import com.smartfinance.auth.dto.request.RegisterRequest;
import com.smartfinance.auth.dto.request.UpdateProfileRequest;
import java.util.UUID;
import com.smartfinance.auth.dto.response.AuthResponse;
import com.smartfinance.auth.dto.response.UserResponse;
import com.smartfinance.auth.entity.AuthProvider;
import com.smartfinance.auth.entity.User;
import com.smartfinance.auth.exception.InvalidCredentialsException;
import com.smartfinance.auth.exception.UserAlreadyExistsException;
import com.smartfinance.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("[AUTH] Registration failed — email already exists: email={}", maskEmail(request.email()));
            throw new UserAlreadyExistsException(request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        // A password é codificada com BCrypt (strength 12) — nunca guardar em claro
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        log.info("[AUTH] New user registered: email={}, userId={}", maskEmail(request.email()), user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> {
                    log.warn("[AUTH] Login failed — user not found: email={}", maskEmail(request.email()));
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("[AUTH] Login failed — wrong password: email={}", maskEmail(request.email()));
            throw new InvalidCredentialsException();
        }
        log.info("[AUTH] Login success: email={}, userId={}", maskEmail(request.email()), user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse refreshAuth(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        log.debug("[AUTH] Auth refreshed for userId={}", userId);
        return buildAuthResponse(user);
    }

    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.setName(request.name());
        log.info("[AUTH] Profile updated: userId={}", userId);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("[AUTH] Password change failed — wrong current password: userId={}", userId);
            throw new InvalidCredentialsException();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("[AUTH] Password changed successfully: userId={}", userId);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getAvatarUrl(), user.getProvider().name());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, jwtConfig.accessTokenExpiration(), toUserResponse(user));
    }

    /**
     * Mascara o email para logging seguro (ex: "jo***@example.com").
     * Garante que o email completo nunca aparece em logs de produção.
     */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (local.length() <= 2) return "**" + domain;
        return local.substring(0, 2) + "***" + domain;
    }
}
