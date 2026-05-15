package com.neuralops.userservice.api.controller;

import com.neuralops.userservice.api.dto.ApiKeyCreateRequest;
import com.neuralops.userservice.api.dto.ApiKeyResponse;
import com.neuralops.userservice.api.dto.AuthResponse.UserProfile;
import com.neuralops.userservice.domain.entity.UserEntity;
import com.neuralops.userservice.domain.repository.UserRepository;
import com.neuralops.userservice.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile and API key management")
public class UserController {

    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's profile")
    public ResponseEntity<UserProfile> getMe(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return ResponseEntity.ok(new UserProfile(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PostMapping("/me/api-keys")
    @Operation(summary = "Create a new API key",
            description = "The raw key is returned once and never stored. Save it immediately.")
    public ResponseEntity<ApiKeyResponse> createApiKey(
            Authentication auth, @Valid @RequestBody ApiKeyCreateRequest request) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.createApiKey(userId, request));
    }

    @GetMapping("/me/api-keys")
    @Operation(summary = "List the current user's active API keys")
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(apiKeyService.listApiKeys(userId));
    }

    @DeleteMapping("/me/api-keys/{keyId}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> revokeApiKey(Authentication auth, @PathVariable Long keyId) {
        Long userId = Long.parseLong(auth.getName());
        apiKeyService.revokeApiKey(userId, keyId);
        return ResponseEntity.noContent().build();
    }
}
