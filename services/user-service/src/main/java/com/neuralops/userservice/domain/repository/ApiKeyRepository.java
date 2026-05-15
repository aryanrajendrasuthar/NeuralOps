package com.neuralops.userservice.domain.repository;

import com.neuralops.userservice.domain.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findByUserIdAndIsActiveTrue(Long userId);

    Optional<ApiKeyEntity> findByKeyPrefixAndIsActiveTrue(String keyPrefix);
}
