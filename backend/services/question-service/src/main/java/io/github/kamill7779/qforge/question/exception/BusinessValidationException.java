package io.github.kamill7779.qforge.question.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class BusinessValidationException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;
    private final HttpStatus httpStatus;

    public BusinessValidationException(String code, String message, Map<String, Object> details) {
        this(code, message, details, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessValidationException(
            String code,
            String message,
            Map<String, Object> details,
            HttpStatus httpStatus
    ) {
        super(message);
        this.code = code;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
