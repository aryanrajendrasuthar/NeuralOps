package com.neuralops.userservice.service;

import com.neuralops.userservice.api.dto.AuthResponse;
import com.neuralops.userservice.api.dto.LoginRequest;
import com.neuralops.userservice.api.dto.RegisterRequest;
import com.neuralops.userservice.domain.entity.UserEntity;
import com.neuralops.userservice.domain.entity.UserEntity.Role;
import com.neuralops.userservice.domain.repository.UserRepository;
import com.neuralops.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "secret-password";
    private static final String FULL_NAME = "Test User";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "test-secret-key-that-is-at-least-32-chars-long-for-hmac", 15L, 30L);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, redisTemplate);
    }

    private UserEntity buildUser(long id) {
        return UserEntity.builder()
                .id(id)
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName(FULL_NAME)
                .role(Role.VIEWER)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void register_returnsTokens_forNewEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        UserEntity saved = buildUser(1L);
        when(userRepository.save(any())).thenReturn(saved);

        AuthResponse response = authService.register(new RegisterRequest(EMAIL, PASSWORD, FULL_NAME));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo(EMAIL);
    }

    @Test
    void register_throwsIllegalArgumentException_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, FULL_NAME)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void login_returnsTokens_withCorrectCredentials() {
        UserEntity user = buildUser(2L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(2L);
    }

    @Test
    void login_throws_whenEmailNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_throws_whenPasswordWrong() {
        UserEntity user = buildUser(3L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_throws_whenAccountDeactivated() {
        UserEntity user = buildUser(4L);
        user.setIsActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void refresh_returnsNewTokens_forValidRefreshToken() {
        UserEntity user = buildUser(5L);
        String refreshToken = jwtService.generateRefreshToken(5L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        AuthResponse response = authService.refresh(refreshToken);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(5L);
    }

    @Test
    void refresh_throws_forRevokedToken() {
        String refreshToken = jwtService.generateRefreshToken(5L);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void refresh_throws_whenAccessTokenPassedInsteadOfRefresh() {
        String accessToken = jwtService.generateAccessToken(5L, EMAIL, "VIEWER");

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a refresh token");
    }

    @Test
    void logout_writesTokenIdToRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String refreshToken = jwtService.generateRefreshToken(6L);

        authService.logout(refreshToken);

        verify(valueOperations).set(anyString(), any(), any());
    }

    @Test
    void logout_silentlyIgnores_invalidToken() {
        authService.logout("not.a.valid.token");
    }
}
