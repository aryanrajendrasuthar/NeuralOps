package com.neuralops.costanalytics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastPoint(
        LocalDate forecastDate,
        int forecastDay,
        BigDecimal predictedCostUsd,
        Double rSquared
) {}
