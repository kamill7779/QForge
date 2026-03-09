package io.github.kamill7779.qforge.gaokaocorpus.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class BusinessValidationException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    public BusinessValidationException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = Map.of();
    }

    public BusinessValidationException(String code, String message, HttpStatus httpStatus, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : Map.of();
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Map<String, Object> getDetails() { return details; }
}
