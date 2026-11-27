package com.broadcastmail.api.feedback;

import com.broadcastmail.api.config.FeedbackProperties;
import com.broadcastmail.api.feedback.dto.FeedbackRequest;
import com.broadcastmail.api.feedback.dto.PublicFeedbackRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "feedback")
@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackProperties feedbackProperties;

    @PostMapping("/api/v1/feedback")
    public ResponseEntity<Void> submitFeedback(@AuthenticationPrincipal UUID accountId,
                                               @RequestBody @Valid FeedbackRequest request) {
        if (!feedbackProperties.enabled()) return ResponseEntity.notFound().build();
        feedbackService.submit(accountId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/public/feedback")
    public ResponseEntity<Void> submitPublicFeedback(@RequestBody @Valid PublicFeedbackRequest request) {
        if (!feedbackProperties.enabled()) return ResponseEntity.notFound().build();
        feedbackService.submitPublic(request);
        return ResponseEntity.noContent().build();
    }
}
