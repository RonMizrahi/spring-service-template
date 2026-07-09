package com.example.template.exceptions;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central error handling for the REST API. Returns RFC 7807 {@link ProblemDetail} bodies — the
 * Spring 6 / Boot 3 standard shape — instead of ad-hoc maps.
 *
 * <p>Only application-level exceptions are handled here. Authorization failures from method
 * security ({@code @AdminOnly}/{@code @UserOnly}) are intentionally left to Spring Security, which
 * already answers 403; a catch-all here would wrongly turn those into 500s.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Validation failed" : detail);
    }

    @ExceptionHandler(CustomException.class)
    public ProblemDetail handleCustom(CustomException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }
}
