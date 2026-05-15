package com.neuralops.traceingestion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Acknowledgement returned after a trace event is accepted")
public record TraceEventResponse(

    @Schema(description = "Server-assigned unique identifier for this trace event", example = "a7f3c2d1-8b4e-4f2a-9c1d-3e5f6a7b8c9d")
    String traceId,

    @Schema(description = "The agentId from the submitted event", example = "customer-support-agent-v2")
    String agentId,

    @Schema(description = "The sessionId from the submitted event", example = "sess_01HXK3M9QT")
    String sessionId,

    @Schema(description = "The traceType from the submitted event")
    TraceType traceType,

    @Schema(description = "Server-side timestamp when the event was ingested")
    Instant ingestedAt,

    @Schema(description = "Status of the ingestion", example = "ACCEPTED")
    String status
) {}
