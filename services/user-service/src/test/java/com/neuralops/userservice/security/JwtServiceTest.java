package com.neuralops.userservice.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars-long-for-hmac";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15L, 30L);
    }

    @Test
    void generateAccessToken_producesValidToken() {
        String token = jwtService.generateAccessToken(42L, "user@example.com", "VIEWER");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        String token = jwtService.generateAccessToken(42L, "user@example.com", "VIEWER");
        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email")).isEqualTo("user@example.com");
        assertThat(claims.get("role")).isEqualTo("VIEWER");
        assertThat(claims.get("type")).isEqualTo("access");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void generateRefreshToken_hasTypeRefresh() {
        String token = jwtService.generateRefreshToken(99L);
        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("99");
        assertThat(claims.get("type")).isEqualTo("refresh");
    }

    @Test
    void isValid_returnsFalse_forTamperedToken() {
        String token = jwtService.generateAccessToken(1L, "a@b.com", "ADMIN");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_returnsFalse_forTokenSignedWithDifferentKey() {
        JwtService otherService = new JwtService("completely-different-secret-key-at-least-32-chars", 15L, 30L);
        String foreignToken = otherService.generateAccessToken(1L, "a@b.com", "VIEWER");

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }

    @Test
    void isValid_returnsFalse_forBlankString() {
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void extractUserId_returnsCorrectId() {
        String token = jwtService.generateAccessToken(77L, "test@test.com", "OPERATOR");

        assertThat(jwtService.extractUserId(token)).isEqualTo(77L);
    }

    @Test
    void extractTokenId_returnsNonBlankJti() {
        String token = jwtService.generateAccessToken(1L, "a@b.com", "VIEWER");

        assertThat(jwtService.extractTokenId(token)).isNotBlank();
    }

    @Test
    void twoAccessTokens_haveDifferentJtis() {
        String token1 = jwtService.generateAccessToken(1L, "a@b.com", "VIEWER");
        String token2 = jwtService.generateAccessToken(1L, "a@b.com", "VIEWER");

        assertThat(jwtService.extractTokenId(token1)).isNotEqualTo(jwtService.extractTokenId(token2));
    }

    @Test
    void getRefreshTokenExpirySeconds_returnsCorrectValue() {
        assertThat(jwtService.getRefreshTokenExpirySeconds()).isEqualTo(30L * 24 * 3600);
    }

    @Test
    void validateAndParse_throwsOnExpiredToken() {
        JwtService shortLivedService = new JwtService(SECRET, 0L, 0L);
        String token = shortLivedService.generateAccessToken(1L, "a@b.com", "VIEWER");

        assertThat(shortLivedService.isValid(token)).isFalse();
    }
}
