package com.neuralops.costanalytics.api.controller;

import com.neuralops.costanalytics.api.dto.CostSummaryResponse;
import com.neuralops.costanalytics.api.dto.ForecastPoint;
import com.neuralops.costanalytics.service.CostQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cost")
@RequiredArgsConstructor
@Tag(name = "Cost Analytics", description = "Daily cost rollups and spend forecasting for AI agents")
public class CostAnalyticsController {

    private final CostQueryService costQueryService;

    @GetMapping("/agents/{agentId}/summary")
    @Operation(
            summary = "Get cost summary for an agent",
            description = "Returns total cost, trace count, and token count aggregated over the " +
                    "requested number of days, with a per-day breakdown."
    )
    public ResponseEntity<CostSummaryResponse> getCostSummary(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId,
            @RequestParam(defaultValue = "30") @Parameter(description = "Days of history (max 365)") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        return ResponseEntity.ok(costQueryService.getCostSummary(agentId, safeDays));
    }

    @GetMapping("/agents/{agentId}/forecast")
    @Operation(
            summary = "Get spend forecast for an agent",
            description = "Returns a linear regression forecast for the next N days based on the " +
                    "agent's historical daily cost. Returns an empty list if there is insufficient " +
                    "data to fit a model (fewer than 3 days of history)."
    )
    public ResponseEntity<List<ForecastPoint>> getForecast(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId) {
        return ResponseEntity.ok(costQueryService.getForecast(agentId));
    }
}
