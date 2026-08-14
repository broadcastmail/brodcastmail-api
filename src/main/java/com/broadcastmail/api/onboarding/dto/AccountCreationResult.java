package com.broadcastmail.api.onboarding.dto;

import java.util.UUID;

public record AccountCreationResult(String rawApiKey, UUID accountId) {}