package com.broadcastmail.api.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank @Size(max = 2000) String message,
        @NotBlank @Pattern(regexp = "app|first_campaign") String source
) {}
