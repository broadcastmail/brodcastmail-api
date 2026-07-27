package com.broadcastemail.api.campaign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum CampaignStatus {
    DRAFT, SENDING, SENT, PARTIALLY_FAILED, FAILED;

    @Converter(autoApply = true)
    public static class PersistenceConverter implements AttributeConverter<CampaignStatus, String> {

        @Override
        public String convertToDatabaseColumn(CampaignStatus status) {
            return status == null ? null : status.name().toLowerCase();
        }

        @Override
        public CampaignStatus convertToEntityAttribute(String value) {
            return value == null ? null : CampaignStatus.valueOf(value.toUpperCase());
        }
    }
}
