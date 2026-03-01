package io.github.kamill7779.qforge.question.config;

import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @SuppressWarnings("null")
    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessValidation(BusinessValidationException ex) {
        return ResponseEntity.status(Objects.requireNonNullElse(ex.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR)).body(Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage(),
                "traceId", UUID.randomUUID().toString().replace("-", ""),
                "details", ex.getDetails()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "REQUEST_VALIDATION_FAILED",
                "message", "Request payload validation failed",
                "traceId", UUID.randomUUID().toString().replace("-", ""),
                "details", Map.of("errors", ex.getBindingResult().toString())
        ));
    }
}
