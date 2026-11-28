package com.broadcastmail.api.connection.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailSourceResult.Detected.class, name = "DETECTED"),
        @JsonSubTypes.Type(value = EmailSourceResult.MultipleCandidates.class, name = "MULTIPLE_CANDIDATES"),
        @JsonSubTypes.Type(value = EmailSourceResult.NotDetected.class, name = "NOT_DETECTED")
})
public sealed interface EmailSourceResult
        permits EmailSourceResult.Detected, EmailSourceResult.MultipleCandidates, EmailSourceResult.NotDetected {

    record Detected(EmailSourceOption option, String emailColumn, List<EmailSourceOption> tables) implements EmailSourceResult {}

    record MultipleCandidates(List<EmailSourceOption> candidates, List<EmailSourceOption> tables) implements EmailSourceResult {}

    record NotDetected(List<EmailSourceOption> tables) implements EmailSourceResult {}
}
