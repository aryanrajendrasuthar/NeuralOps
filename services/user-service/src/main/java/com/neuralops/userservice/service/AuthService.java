package com.neuralops.userservice.service;

import com.neuralops.userservice.api.dto.AuthResponse;
import com.neuralops.userservice.api.dto.LoginRequest;
import com.neuralops.userservice.api.dto.RegisterRequest;
import com.neuralops.userservice.domain.entity.UserEntity;
import com.neuralops.userservice.domain.entity.UserEntity.Role;
import com.neuralops.userservice.domain.repository.UserRepository;
import com.neuralops.userservice.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        Instant now = Instant.now();
        UserEntity user = UserEntity.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.VIEWER)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);

        log.info("New user registered: email={} id={}", user.getEmail(), user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!user.getIsActive()) {
            throw new IllegalArgumentException("This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        log.info("User login: email={} id={}", user.getEmail(), user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token.");
        }

        Claims claims = jwtService.validateAndParse(refreshToken);
        if (!"refresh".equals(claims.get("type"))) {
            throw new IllegalArgumentException("Token is not a refresh token.");
        }

        String tokenId = claims.getId();
        String redisKey = REFRESH_KEY_PREFIX + claims.getSubject() + ":" + tokenId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new IllegalArgumentException("Refresh token has been revoked.");
        }

        Long userId = Long.parseLong(claims.getSubject());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        return buildAuthResponse(user);
    }

    public void logout(String refreshToken) {
        if (!jwtService.isValid(refreshToken)) return;
        Claims claims = jwtService.validateAndParse(refreshToken);
        String tokenId = claims.getId();
        String redisKey = REFRESH_KEY_PREFIX + claims.getSubject() + ":" + tokenId;
        redisTemplate.opsForValue().set(redisKey, "revoked",
                Duration.ofSeconds(jwtService.getRefreshTokenExpirySeconds()));
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                ACCESS_TOKEN_EXPIRY_SECONDS,
                "Bearer",
                new AuthResponse.UserProfile(
                        user.getId(), user.getEmail(), user.getFullName(), user.getRole().name())
        );
    }
}
