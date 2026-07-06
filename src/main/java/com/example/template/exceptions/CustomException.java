package com.example.template.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Application exception that carries the HTTP status it should map to, so business code can
 * signal a 404/409/etc. without hand-building a response. {@link GlobalExceptionHandler} turns
 * it into an RFC 7807 {@link org.springframework.http.ProblemDetail}.
 */
public class CustomException extends RuntimeException {

    private final HttpStatus status;

    public CustomException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
