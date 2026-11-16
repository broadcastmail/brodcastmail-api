package com.broadcastmail.api.emailprovider.dto;

import java.util.List;

public record VerifyKeyResponse(
        List<String> domains
) {}
