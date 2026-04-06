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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return buildAuthResponse(user);
    }

    public AuthResponse refreshAuth(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
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
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getAvatarUrl(), user.getProvider().name());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, jwtConfig.accessTokenExpiration(), toUserResponse(user));
    }
}