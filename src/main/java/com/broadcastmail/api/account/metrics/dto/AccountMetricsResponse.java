package com.broadcastmail.api.account.metrics.dto;

public record AccountMetricsResponse(
        int audience, // Total number of recipients
        String audienceSource,  // Name of the Supabase source table for recipients
        int totalDeliveredThisMonth, // Number of emails delivered this month
        double deliveryRate, // Percentage of emails delivered
        long recipientsUsedThisPeriod, // Number of recipients used this period
        int recipientsLimit // Limit of recipients for the account
) {}
