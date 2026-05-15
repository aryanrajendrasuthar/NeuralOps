package com.neuralops.traceingestion.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "A single trace event emitted by an AI agent")
public record TraceEventRequest(

    @NotBlank(message = "agentId is required and must not be blank")
    @Size(max = 255, message = "agentId must not exceed 255 characters")
    @Schema(description = "Unique identifier of the AI agent emitting this event", example = "customer-support-agent-v2")
    @JsonProperty("agentId")
    String agentId,

    @NotBlank(message = "sessionId is required and must not be blank")
    @Size(max = 255, message = "sessionId must not exceed 255 characters")
    @Schema(description = "Identifier for the current agent session or conversation", example = "sess_01HXK3M9QT")
    @JsonProperty("sessionId")
    String sessionId,

    @NotNull(message = "traceType is required")
    @Schema(description = "The type of event being traced", example = "LLM_CALL")
    @JsonProperty("traceType")
    TraceType traceType,

    @Schema(description = "Arbitrary structured payload specific to the trace type")
    @JsonProperty("payload")
    Map<String, Object> payload,

    @NotNull(message = "latencyMs is required")
    @Min(value = 0, message = "latencyMs must be non-negative")
    @Schema(description = "Duration of the operation in milliseconds", example = "1843")
    @JsonProperty("latencyMs")
    Long latencyMs,

    @Min(value = 0, message = "tokenCount must be non-negative")
    @Schema(description = "Total token count for LLM calls (prompt + completion)", example = "1559")
    @JsonProperty("tokenCount")
    Integer tokenCount,

    @DecimalMin(value = "0.0", message = "estimatedCostUsd must be non-negative")
    @Schema(description = "Estimated cost in USD for this operation", example = "0.0023")
    @JsonProperty("estimatedCostUsd")
    BigDecimal estimatedCostUsd,

    @Schema(description = "ISO-8601 timestamp of when the event occurred. Defaults to server time if omitted.")
    @JsonProperty("timestamp")
    Instant timestamp,

    @Schema(description = "Arbitrary string metadata attached to this event")
    @JsonProperty("metadata")
    Map<String, String> metadata
) {}
