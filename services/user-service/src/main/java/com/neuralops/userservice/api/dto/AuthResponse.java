package com.neuralops.userservice.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        String tokenType,
        UserProfile user
) {
    public record UserProfile(Long id, String email, String fullName, String role) {}
}
