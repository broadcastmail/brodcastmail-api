package com.broadcastemail.api.outbox;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum OutboxStatus {
    PENDING, PROCESSING, DONE, FAILED;
    @Converter
    public static class PersistenceConverter implements AttributeConverter<OutboxStatus, String> {

        @Override
        public String convertToDatabaseColumn(OutboxStatus status) {
            return status == null ? null : status.name().toLowerCase();
        }

        @Override
        public OutboxStatus convertToEntityAttribute(String value) {
            return value == null ? null : OutboxStatus.valueOf(value.toUpperCase());
        }
    }
}
