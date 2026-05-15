package com.neuralops.traceingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces RFC 7807 Problem Details responses for all error conditions.
 * Clients receive structured, parseable error payloads — never raw stack traces.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URI = "https://neuralops.io/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        field -> field.getDefaultMessage() != null ? field.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(ERROR_BASE_URI + "validation-failure"));
        problem.setTitle("Trace event validation failed");
        problem.setDetail("One or more fields failed validation. See 'fieldErrors' for details.");
        problem.setProperty("fieldErrors", fieldErrors);

        log.debug("Validation failure: {}", fieldErrors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(ERROR_BASE_URI + "invalid-request"));
        problem.setTitle("Invalid request");
        problem.setDetail(ex.getMessage());

        log.debug("Invalid request: {}", ex.getMessage());
        return problem;
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleServiceUnavailable(ServiceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create(ERROR_BASE_URI + "service-unavailable"));
        problem.setTitle("Service temporarily unavailable");
        problem.setDetail(ex.getMessage());

        log.warn("Service unavailable: {}", ex.getMessage());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(ERROR_BASE_URI + "internal-error"));
        problem.setTitle("An unexpected error occurred");
        problem.setDetail("The server encountered an error processing your request. " +
                          "Contact support if this persists.");

        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return problem;
    }
}
