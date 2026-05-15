package com.neuralops.costanalytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "agent_cost_forecast")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCostForecastEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "forecast_day", nullable = false)
    private Integer forecastDay;

    @Column(name = "predicted_cost", nullable = false, precision = 14, scale = 8)
    private BigDecimal predictedCost;

    @Column(name = "r_squared")
    private Double rSquared;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
