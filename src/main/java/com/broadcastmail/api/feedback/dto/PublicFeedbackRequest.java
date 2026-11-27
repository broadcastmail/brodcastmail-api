package com.broadcastmail.api.feedback.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicFeedbackRequest(
        @NotBlank @Size(max = 2000) String message,
        @Email @Size(max = 320) String email,
        @Size(max = 200) String page,
        @NotBlank String turnstileToken
) {}
