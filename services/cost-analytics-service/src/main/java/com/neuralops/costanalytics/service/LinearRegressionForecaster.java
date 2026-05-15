package com.neuralops.costanalytics.service;

import com.neuralops.costanalytics.domain.entity.AgentCostForecastEntity;
import com.neuralops.costanalytics.domain.entity.AgentDailyCostEntity;
import com.neuralops.costanalytics.domain.repository.AgentCostForecastRepository;
import com.neuralops.costanalytics.domain.repository.AgentDailyCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinearRegressionForecaster {

    private static final int MIN_DATA_POINTS = 3;

    private final AgentDailyCostRepository agentDailyCostRepository;
    private final AgentCostForecastRepository agentCostForecastRepository;

    @Value("${neuralops.cost.forecast-days:7}")
    private int forecastDays;

    @Value("${neuralops.cost.history-days-for-forecast:30}")
    private int historyDays;

    public List<AgentCostForecastEntity> forecastForAgent(String agentId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(historyDays);

        List<AgentDailyCostEntity> history = agentDailyCostRepository
                .findByAgentIdAndCostDateBetweenOrderByCostDateAsc(agentId, from, today.minusDays(1));

        if (history.size() < MIN_DATA_POINTS) {
            log.debug("Insufficient data to forecast for agentId={}: {} data points (min {})",
                    agentId, history.size(), MIN_DATA_POINTS);
            return List.of();
        }

        SimpleRegression regression = new SimpleRegression();
        LocalDate baseDate = history.get(0).getCostDate();
        for (AgentDailyCostEntity row : history) {
            long dayIndex = baseDate.until(row.getCostDate(), java.time.temporal.ChronoUnit.DAYS);
            regression.addData(dayIndex, row.getTotalCostUsd().doubleValue());
        }

        long nextDayIndex = baseDate.until(today, java.time.temporal.ChronoUnit.DAYS);
        double rSquared = regression.getRSquare();
        Instant now = Instant.now();

        List<AgentCostForecastEntity> forecasts = new ArrayList<>(forecastDays);
        for (int day = 1; day <= forecastDays; day++) {
            double predicted = regression.predict(nextDayIndex + day);
            BigDecimal predictedCost = BigDecimal.valueOf(Math.max(0.0, predicted))
                    .setScale(8, RoundingMode.HALF_UP);

            forecasts.add(AgentCostForecastEntity.builder()
                    .agentId(agentId)
                    .forecastDate(today)
                    .forecastDay(day)
                    .predictedCost(predictedCost)
                    .rSquared(Double.isNaN(rSquared) ? null : rSquared)
                    .generatedAt(now)
                    .build());
        }
        return forecasts;
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runDailyForecastJob() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.info("Running daily cost forecast job for date={}", today);

        List<String> agentIds = agentDailyCostRepository
                .findAll()
                .stream()
                .map(AgentDailyCostEntity::getAgentId)
                .distinct()
                .toList();

        int generated = 0;
        for (String agentId : agentIds) {
            try {
                List<AgentCostForecastEntity> forecasts = forecastForAgent(agentId);
                if (!forecasts.isEmpty()) {
                    agentCostForecastRepository.deleteByAgentIdAndForecastDate(agentId, today);
                    agentCostForecastRepository.saveAll(forecasts);
                    generated++;
                }
            } catch (Exception ex) {
                log.error("Forecast failed for agentId={}: {}", agentId, ex.getMessage(), ex);
            }
        }
        log.info("Daily cost forecast job complete: {} agents updated", generated);
    }
}
