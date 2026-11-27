package com.broadcastmail.api.common;

import com.broadcastmail.api.common.exceptions.*;
import com.broadcastmail.common.unsubscribe.InvalidUnsubscribeTokenException;
import com.broadcastmail.common.account.plan.PlanLimitExceededException;
import com.stripe.exception.StripeException;
import io.sentry.Sentry;
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
        Sentry.captureException(ex);
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

    @ExceptionHandler(CampaignContentMissingException.class)
    public ResponseEntity<Map<String, String>> handleCampaignContentMissing(CampaignContentMissingException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCampaignFilterException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCampaignFilter(InvalidCampaignFilterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(SupabaseReauthRequiredException.class)
    public ResponseEntity<Map<String, String>> handleSupabaseReauthRequired(SupabaseReauthRequiredException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, SupabaseReauthRequiredException.CODE));
    }

    @ExceptionHandler(ReaderNotReadyException.class)
    public ResponseEntity<Map<String, String>> handleReaderNotReady(ReaderNotReadyException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
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
    @ExceptionHandler(StripeException.class)
    public ResponseEntity<Map<String, String>> handleStripeException(StripeException ex) {
        Sentry.captureException(ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(ERROR_KEY, "Payment service error"));
    }
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAccountNotFoundException(AccountNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_KEY, "Account not found"));
    }

    @ExceptionHandler(CampaignNotRetryableException.class)
    public ResponseEntity<Map<String, String>> handleCampaignNotRetryableException(CampaignNotRetryableException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(CampaignInProgressException.class)
    public ResponseEntity<Map<String, String>> handleCampaignInProgress(CampaignInProgressException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        ERROR_KEY, ex.getMessage(),
                        "campaignId", ex.getCampaignId().toString(),
                        "campaignName", ex.getCampaignName()));
    }

    @ExceptionHandler(ResendDailyLimitActiveException.class)
    public ResponseEntity<Map<String, String>> handleResendDailyLimitActive(ResendDailyLimitActiveException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(PlanFeatureException.class)
    public ResponseEntity<Map<String, String>> handlePlanFeatureException(PlanFeatureException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        ERROR_KEY, "Feature not available on current plan",
                        "feature", ex.getFeature().name()
                ));
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handlePlanLimitExceededException(PlanLimitExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(TurnstileVerificationException.class)
    public ResponseEntity<Map<String, String>> handleTurnstileVerification(TurnstileVerificationException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyTaken(EmailAlreadyTakenException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_KEY, ex.getMessage()));
    }
}