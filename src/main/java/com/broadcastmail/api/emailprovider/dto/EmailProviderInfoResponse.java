package com.broadcastmail.api.emailprovider.dto;

public record EmailProviderInfoResponse(
        String fromAddress,
        String fromName,
        String keyTail
) {}
