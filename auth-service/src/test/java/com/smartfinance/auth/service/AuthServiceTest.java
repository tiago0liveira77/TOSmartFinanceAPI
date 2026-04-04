package com.smartfinance.auth.service;

import com.smartfinance.auth.config.JwtConfig;
import com.smartfinance.auth.dto.request.LoginRequest;
import com.smartfinance.auth.dto.request.RegisterRequest;
import com.smartfinance.auth.dto.response.AuthResponse;
import com.smartfinance.auth.entity.AuthProvider;
import com.smartfinance.auth.entity.User;
import com.smartfinance.auth.exception.InvalidCredentialsException;
import com.smartfinance.auth.exception.UserAlreadyExistsException;
import com.smartfinance.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    JwtConfig jwtConfig;

    @InjectMocks
    AuthService authService;

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private static final String EMAIL = "test@smartfinance.pt";
    private static final String PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$12$hashed";
    private static final String NAME = "Test User";
    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.test.token";
    private static final long EXPIRES_IN = 900L;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail(EMAIL);
        existingUser.setPassword(ENCODED_PASSWORD);
        existingUser.setName(NAME);
        existingUser.setProvider(AuthProvider.LOCAL);
    }

    // ─── register() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("com email novo deve criar utilizador e devolver AuthResponse")
        void withNewEmail_shouldCreateUserAndReturnAuthResponse() {
            // given
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
            given(jwtService.generateAccessToken(any(UUID.class), anyString())).willReturn(ACCESS_TOKEN);
            given(jwtConfig.accessTokenExpiration()).willReturn(EXPIRES_IN);

            // when
            AuthResponse response = authService.register(request);

            // then
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.expiresIn()).isEqualTo(EXPIRES_IN);
            assertThat(response.user().email()).isEqualTo(EMAIL);
            assertThat(response.user().name()).isEqualTo(NAME);
            assertThat(response.user().provider()).isEqualTo("LOCAL");
        }

        @Test
        @DisplayName("deve encriptar a password antes de guardar")
        void shouldEncodePasswordBeforeSaving() {
            // given
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
            given(jwtService.generateAccessToken(any(), any())).willReturn(ACCESS_TOKEN);
            given(jwtConfig.accessTokenExpiration()).willReturn(EXPIRES_IN);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // when
            authService.register(request);

            // then
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(PASSWORD);
        }

        @Test
        @DisplayName("deve definir provider como LOCAL")
        void shouldSetProviderAsLocal() {
            // given
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
            given(jwtService.generateAccessToken(any(), any())).willReturn(ACCESS_TOKEN);
            given(jwtConfig.accessTokenExpiration()).willReturn(EXPIRES_IN);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // when
            authService.register(request);

            // then
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);
        }

        @Test
        @DisplayName("com email já existente deve lançar UserAlreadyExistsException")
        void withExistingEmail_shouldThrowUserAlreadyExistsException() {
            // given
            RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            // when / then
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(EMAIL);

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    // ─── login() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("com credenciais válidas deve devolver AuthResponse")
        void withValidCredentials_shouldReturnAuthResponse() {
            // given
            LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
            given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(existingUser));
            given(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtService.generateAccessToken(existingUser.getId(), EMAIL)).willReturn(ACCESS_TOKEN);
            given(jwtConfig.accessTokenExpiration()).willReturn(EXPIRES_IN);

            // when
            AuthResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.expiresIn()).isEqualTo(EXPIRES_IN);
            assertThat(response.user().email()).isEqualTo(EMAIL);
            assertThat(response.user().id()).isEqualTo(existingUser.getId());
        }

        @Test
        @DisplayName("com email não registado deve lançar InvalidCredentialsException")
        void withUnknownEmail_shouldThrowInvalidCredentialsException() {
            // given
            LoginRequest request = new LoginRequest("desconhecido@test.pt", PASSWORD);
            given(userRepository.findByEmailAndDeletedAtIsNull("desconhecido@test.pt"))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("com password errada deve lançar InvalidCredentialsException")
        void withWrongPassword_shouldThrowInvalidCredentialsException() {
            // given
            LoginRequest request = new LoginRequest(EMAIL, "password_errada");
            given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(existingUser));
            given(passwordEncoder.matches("password_errada", ENCODED_PASSWORD)).willReturn(false);

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateAccessToken(any(), any());
        }

        @Test
        @DisplayName("mensagem de erro não deve distinguir email vs password (timing safe)")
        void errorMessage_shouldNotRevealWhetherEmailOrPasswordIsWrong() {
            // given — email inexistente
            LoginRequest request = new LoginRequest("nao-existe@test.pt", PASSWORD);
            given(userRepository.findByEmailAndDeletedAtIsNull("nao-existe@test.pt"))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }
    }
}
