package com.email.bulk.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.email.bulk.exception.CampaignNotFoundException;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.email.bulk.exception.InvalidImportFileException;
import com.email.bulk.exception.RecipientImportNotFoundException;
import com.email.bulk.exception.CampaignConflictException;
import com.email.bulk.exception.InvalidCampaignListRequestException;

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

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCampaignNotFound(
            CampaignNotFoundException exception) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        404,
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidImportFileException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidImportFile(
            InvalidImportFileException exception) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        400,
                        exception.getMessage()
                ));
    }

    public record ApiErrorResponse(
            int status,
            String message
    ) {
    }

    @ExceptionHandler(RecipientImportNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleImportNotFound(
            RecipientImportNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(CampaignConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleCampaignConflict(
            CampaignConflictException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidCampaignListRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCampaignListRequest(
            InvalidCampaignListRequestException exception) {

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        exception.getMessage()
                ));
    }

}