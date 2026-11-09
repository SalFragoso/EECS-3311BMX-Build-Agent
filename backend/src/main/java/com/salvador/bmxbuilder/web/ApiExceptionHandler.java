package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions from any controller into clean JSON errors with the right status code,
 * instead of a generic 500 page.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    // Unknown build or part id -> 404
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    // Rule broken in the service or domain (min > max, part in the wrong slot) -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    // The LLM couldn't be reached and the feature had no fallback -> 503
    @ExceptionHandler(LLMException.class)
    public ResponseEntity<ApiError> aiUnavailable(LLMException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(e.getMessage()));
    }

    // Bad path/query value, e.g. /api/builds/1/parts/FROG -> 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badValue(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("Invalid value '" + e.getValue() + "' for " + e.getName()));
    }

    // Bad search filter, e.g. /api/parts?category=FROG -> 400
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> badFilter(BindException e) {
        FieldError field = e.getFieldError();
        String message = (field == null)
                ? "Invalid request"
                : "Invalid value '" + field.getRejectedValue() + "' for " + field.getField();
        return ResponseEntity.badRequest().body(new ApiError(message));
    }
}
