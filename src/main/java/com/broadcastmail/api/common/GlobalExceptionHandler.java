package com.broadcastmail.api.common;

import com.broadcastmail.api.common.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_KEY="error";

    @ExceptionHandler(OAuthStateValidationException.class)
    public ResponseEntity<Map<String, String>> handleOAuthStateValidation(
            OAuthStateValidationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(
            IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(NoSupabaseProjectsException.class)
    public ResponseEntity<Map<String, String>> handleNoProjects(
            NoSupabaseProjectsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(InvalidOnboardingSessionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOnboardingSession(
            InvalidOnboardingSessionException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(InvalidResendApiKeyException.class)
    public ResponseEntity<Map<String, String>> handleInvalidResendApiKey(
            InvalidResendApiKeyException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCampaignNotFound(CampaignNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(CampaignNotEditableException.class)
    public ResponseEntity<Map<String, String>> handleCampaignNotEditable(CampaignNotEditableException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleConnectionNotFound(ConnectionNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }



    @ExceptionHandler(InvalidUnsubscribeTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidUnsubscribeToken(InvalidUnsubscribeTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }
}