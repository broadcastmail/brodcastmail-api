package com.broadcastmail.api.oauth.dto;

public record ProcessCallbackRequest(
        String code,
        String state,
        String storedState,
        String codeVerifier,
        String redirectUri,
        String bmSession
) {}
