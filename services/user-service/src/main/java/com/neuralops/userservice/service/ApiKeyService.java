package com.neuralops.userservice.service;

import com.neuralops.userservice.api.dto.ApiKeyCreateRequest;
import com.neuralops.userservice.api.dto.ApiKeyResponse;
import com.neuralops.userservice.domain.entity.ApiKeyEntity;
import com.neuralops.userservice.domain.entity.UserEntity;
import com.neuralops.userservice.domain.repository.ApiKeyRepository;
import com.neuralops.userservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_KEY_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${neuralops.api-key.prefix:nops_}")
    private String keyPrefix;

    @Transactional
    public ApiKeyResponse createApiKey(Long userId, ApiKeyCreateRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        byte[] randomBytes = new byte[RAW_KEY_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawKey = keyPrefix + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String displayPrefix = rawKey.substring(0, Math.min(rawKey.length(), 12));

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .user(user)
                .name(request.name())
                .keyPrefix(displayPrefix)
                .keyHash(passwordEncoder.encode(rawKey))
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        entity = apiKeyRepository.save(entity);

        log.info("API key created: name={} userId={}", request.name(), userId);
        return new ApiKeyResponse(entity.getId(), entity.getName(), entity.getKeyPrefix(),
                entity.getCreatedAt(), null, rawKey);
    }

    public List<ApiKeyResponse> listApiKeys(Long userId) {
        return apiKeyRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(k -> ApiKeyResponse.withoutRawKey(k.getId(), k.getName(), k.getKeyPrefix(),
                        k.getCreatedAt(), k.getLastUsedAt()))
                .toList();
    }

    @Transactional
    public void revokeApiKey(Long userId, Long keyId) {
        ApiKeyEntity key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + keyId));
        if (!key.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("API key does not belong to this user.");
        }
        key.setIsActive(false);
        apiKeyRepository.save(key);
        log.info("API key revoked: keyId={} userId={}", keyId, userId);
    }
}
