package com.neuralops.alertservice.domain.repository;

import com.neuralops.alertservice.domain.entity.AlertEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long> {

    Page<AlertEventEntity> findByAgentIdOrderByTriggeredAtDesc(String agentId, Pageable pageable);

    List<AlertEventEntity> findByWebhookStatusInOrderByTriggeredAtAsc(
            List<AlertEventEntity.WebhookStatus> statuses);

    long countByAgentIdAndTriggeredAtAfter(String agentId, Instant since);
}
