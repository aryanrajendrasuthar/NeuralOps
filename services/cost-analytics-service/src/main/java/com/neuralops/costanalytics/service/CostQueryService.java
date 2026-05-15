package com.neuralops.costanalytics.service;

import com.neuralops.costanalytics.api.dto.CostSummaryResponse;
import com.neuralops.costanalytics.api.dto.DailyCostPoint;
import com.neuralops.costanalytics.api.dto.ForecastPoint;
import com.neuralops.costanalytics.domain.entity.AgentCostForecastEntity;
import com.neuralops.costanalytics.domain.entity.AgentDailyCostEntity;
import com.neuralops.costanalytics.domain.repository.AgentCostForecastRepository;
import com.neuralops.costanalytics.domain.repository.AgentDailyCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostQueryService {

    private final AgentDailyCostRepository agentDailyCostRepository;
    private final AgentCostForecastRepository agentCostForecastRepository;
    private final LinearRegressionForecaster forecaster;

    public CostSummaryResponse getCostSummary(String agentId, int daysBack) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(daysBack);

        List<AgentDailyCostEntity> rows = agentDailyCostRepository
                .findByAgentIdAndCostDateBetweenOrderByCostDateAsc(agentId, from, today);

        BigDecimal total = rows.stream()
                .map(AgentDailyCostEntity::getTotalCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalTraces = rows.stream().mapToLong(AgentDailyCostEntity::getTraceCount).sum();
        long totalTokens = rows.stream().mapToLong(AgentDailyCostEntity::getTokenCount).sum();

        List<DailyCostPoint> daily = rows.stream()
                .map(r -> new DailyCostPoint(r.getCostDate(), r.getTotalCostUsd(),
                        r.getTraceCount(), r.getTokenCount()))
                .toList();

        return new CostSummaryResponse(agentId, total, totalTraces, totalTokens, daily);
    }

    public List<ForecastPoint> getForecast(String agentId) {
        List<AgentCostForecastEntity> stored = agentCostForecastRepository
                .findLatestForecastForAgent(agentId);

        List<AgentCostForecastEntity> forecasts = stored.isEmpty()
                ? forecaster.forecastForAgent(agentId)
                : stored;

        return forecasts.stream()
                .map(f -> new ForecastPoint(f.getForecastDate(), f.getForecastDay(),
                        f.getPredictedCost(), f.getRSquared()))
                .toList();
    }
}
