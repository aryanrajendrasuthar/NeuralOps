package com.neuralops.alertservice.api.controller;

import com.neuralops.alertservice.api.dto.AlertEventResponse;
import com.neuralops.alertservice.api.dto.AlertRuleRequest;
import com.neuralops.alertservice.api.dto.AlertRuleResponse;
import com.neuralops.alertservice.service.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert Rules", description = "Manage threshold and anomaly alert rules and view alert history")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @PostMapping("/rules")
    @Operation(summary = "Create an alert rule",
            description = "Creates a new enabled alert rule. When the rule condition is met and the " +
                    "deduplication window has expired, the service fires an HTTP POST to the configured " +
                    "webhook URL.")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRuleService.createRule(request));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get an alert rule by ID")
    public ResponseEntity<AlertRuleResponse> getRule(
            @PathVariable @Parameter(description = "Alert rule ID") Long id) {
        return ResponseEntity.ok(alertRuleService.getRule(id));
    }

    @GetMapping("/rules")
    @Operation(summary = "List all alert rules")
    public ResponseEntity<List<AlertRuleResponse>> listRules() {
        return ResponseEntity.ok(alertRuleService.listRules());
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update an alert rule")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable Long id, @Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(alertRuleService.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete an alert rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        alertRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rules/{id}/enabled")
    @Operation(summary = "Enable or disable an alert rule",
            description = "Disabling a rule stops it from firing without deleting its configuration.")
    public ResponseEntity<AlertRuleResponse> setEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            throw new IllegalArgumentException("Request body must contain 'enabled' field");
        }
        return ResponseEntity.ok(alertRuleService.setEnabled(id, enabled));
    }

    @GetMapping("/agents/{agentId}/history")
    @Operation(summary = "Get alert history for an agent",
            description = "Returns a paginated list of alert events fired for the given agent, " +
                    "most recent first.")
    public ResponseEntity<Page<AlertEventResponse>> getAlertHistory(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(size, 100);
        return ResponseEntity.ok(alertRuleService.getAlertHistory(agentId, page, safeSize));
    }
}
