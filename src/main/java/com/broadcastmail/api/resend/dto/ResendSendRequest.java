package com.broadcastmail.api.resend.dto;

import java.util.List;

public record ResendSendRequest(
        String from,
        List<String> to,
        String subject,
        String html
) {}
