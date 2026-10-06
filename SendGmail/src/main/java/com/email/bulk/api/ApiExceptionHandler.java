package com.email.bulk.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, List<String>> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> {
                    errors.computeIfAbsent(
                            error.getField(),
                            key -> new ArrayList<>()
                    ).add(error.getDefaultMessage());
                });

        return ResponseEntity.badRequest().body(
                new ValidationErrorResponse(
                        400,
                        "Request validation failed",
                        errors
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {

        return ResponseEntity.badRequest().body(
                new ValidationErrorResponse(
                        400,
                        "Request body is missing or unreadable",
                        Map.of()
                )
        );
    }

    public record ValidationErrorResponse(
            int status,
            String message,
            Map<String, List<String>> errors
    ) {
    }
}