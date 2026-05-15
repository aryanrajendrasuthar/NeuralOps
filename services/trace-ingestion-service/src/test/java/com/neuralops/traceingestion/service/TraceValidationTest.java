package com.neuralops.traceingestion.service;

import com.neuralops.traceingestion.api.dto.TraceEventRequest;
import com.neuralops.traceingestion.api.dto.TraceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TraceValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("valid request passes all constraints")
    void validRequest_noViolations() {
        TraceEventRequest request = new TraceEventRequest(
                "agent-1", "sess-1", TraceType.LLM_CALL,
                null, 500L, 100, new BigDecimal("0.001"), null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("blank agentId produces a constraint violation")
    void blankAgentId_producesViolation() {
        TraceEventRequest request = new TraceEventRequest(
                "", "sess-1", TraceType.LLM_CALL,
                null, 500L, null, null, null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("agentId"));
    }

    @Test
    @DisplayName("null sessionId produces a constraint violation")
    void nullSessionId_producesViolation() {
        TraceEventRequest request = new TraceEventRequest(
                "agent-1", null, TraceType.LLM_CALL,
                null, 500L, null, null, null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("sessionId"));
    }

    @Test
    @DisplayName("null traceType produces a constraint violation")
    void nullTraceType_producesViolation() {
        TraceEventRequest request = new TraceEventRequest(
                "agent-1", "sess-1", null,
                null, 500L, null, null, null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("traceType"));
    }

    @Test
    @DisplayName("negative latencyMs produces a constraint violation")
    void negativeLatencyMs_producesViolation() {
        TraceEventRequest request = new TraceEventRequest(
                "agent-1", "sess-1", TraceType.ERROR,
                null, -1L, null, null, null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("latencyMs"));
    }

    @Test
    @DisplayName("negative estimatedCostUsd produces a constraint violation")
    void negativeCostUsd_producesViolation() {
        TraceEventRequest request = new TraceEventRequest(
                "agent-1", "sess-1", TraceType.LLM_CALL,
                null, 100L, null, new BigDecimal("-0.001"), null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("estimatedCostUsd"));
    }

    @Test
    @DisplayName("multiple missing required fields produce multiple violations")
    void multipleInvalidFields_producesMultipleViolations() {
        TraceEventRequest request = new TraceEventRequest(
                null, null, null, null, null, null, null, null, null
        );

        Set<ConstraintViolation<TraceEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }
}
