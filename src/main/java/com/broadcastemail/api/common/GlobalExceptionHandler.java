package com.broadcastemail.api.common;

import com.broadcastemail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastemail.api.common.exceptions.InvalidResendApiKeyException;
import com.broadcastemail.api.common.exceptions.NoSupabaseProjectsException;
import com.broadcastemail.api.common.exceptions.OAuthStateValidationException;
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
}