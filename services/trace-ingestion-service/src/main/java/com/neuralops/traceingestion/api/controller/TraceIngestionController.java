package com.neuralops.traceingestion.api.controller;

import com.neuralops.traceingestion.api.dto.TraceEventRequest;
import com.neuralops.traceingestion.api.dto.TraceEventResponse;
import com.neuralops.traceingestion.service.TraceIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
@Tag(name = "Trace Ingestion", description = "Submit trace events from AI agents")
public class TraceIngestionController {

    private final TraceIngestionService traceIngestionService;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Submit a trace event",
            description = "Accepts a single trace event from an AI agent. The event is validated, " +
                          "published to Kafka, and persisted. Returns 202 Accepted immediately after " +
                          "Kafka acknowledgment — downstream processing is asynchronous."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Trace event accepted for processing"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure — missing required fields or invalid values",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Service temporarily unavailable — Kafka circuit breaker open",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public TraceEventResponse ingestTrace(@Valid @RequestBody TraceEventRequest request) {
        return traceIngestionService.ingest(request);
    }
}
