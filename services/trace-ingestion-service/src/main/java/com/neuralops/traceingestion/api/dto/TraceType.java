package com.neuralops.traceingestion.api.dto;

public enum TraceType {
    LLM_CALL,
    TOOL_INVOCATION,
    DECISION_POINT,
    ERROR
}
