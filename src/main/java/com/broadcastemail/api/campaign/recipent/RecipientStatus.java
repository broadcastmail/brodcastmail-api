package com.broadcastemail.api.campaign.recipent;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum RecipientStatus {
    QUEUED, SENT, DELIVERED, OPENED, BOUNCED, FAILED, UNSUBSCRIBED;

    @Converter(autoApply = true)
    public static class PersistenceConverter implements AttributeConverter<RecipientStatus, String> {

        @Override
        public String convertToDatabaseColumn(RecipientStatus status) {
            return status == null ? null : status.name().toLowerCase();
        }

        @Override
        public RecipientStatus convertToEntityAttribute(String value) {
            return value == null ? null : RecipientStatus.valueOf(value.toUpperCase());
        }
    }
}
