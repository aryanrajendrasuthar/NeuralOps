package com.neuralops.userservice.api.dto;

import java.time.Instant;

public record ApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        Instant createdAt,
        Instant lastUsedAt,
        String rawKey
) {
    public static ApiKeyResponse withoutRawKey(Long id, String name, String keyPrefix,
                                               Instant createdAt, Instant lastUsedAt) {
        return new ApiKeyResponse(id, name, keyPrefix, createdAt, lastUsedAt, null);
    }
}
