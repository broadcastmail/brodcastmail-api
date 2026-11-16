package com.broadcastmail.api.connection;

import java.util.Map;
import java.util.Set;

public class ColumnRecommendationService {

    public record Recommendation(boolean recommended, String reason, int rank) {
        static final Recommendation NONE = new Recommendation(false, null, Integer.MAX_VALUE);
    }

    private record Entry(String reason, int rank, Set<String> validDataTypes) {}

    private static final Set<String> TEXT_ENUM = Set.of("text", "enum");
    private static final Set<String> BOOLEAN    = Set.of("boolean");
    private static final Set<String> DATETIME   = Set.of("datetime");

    private static final String COMMON_SEGMENTATION_FIELD = "Common subscription segmentation field";

    private static final Map<String, Entry> DICTIONARY = Map.ofEntries(
            Map.entry("plan",              new Entry(COMMON_SEGMENTATION_FIELD, 1, TEXT_ENUM)),
            Map.entry("tier",              new Entry(COMMON_SEGMENTATION_FIELD, 1, TEXT_ENUM)),
            Map.entry("subscription_plan", new Entry(COMMON_SEGMENTATION_FIELD, 1, TEXT_ENUM)),
            Map.entry("status",            new Entry("Common account status field", 2, TEXT_ENUM)),
            Map.entry("account_status",    new Entry("Common account status field", 2, TEXT_ENUM)),
            Map.entry("country",           new Entry("Useful for geographic segmentation", 3, TEXT_ENUM)),
            Map.entry("country_code",      new Entry("Useful for geographic segmentation", 3, TEXT_ENUM)),
            Map.entry("is_active",         new Entry("Useful for targeting active users", 4, BOOLEAN)),
            Map.entry("active",            new Entry("Useful for targeting active users", 4, BOOLEAN)),
            Map.entry("created_at",        new Entry("Useful for lifecycle segmentation", 5, DATETIME)),
            Map.entry("signed_up_at",      new Entry("Useful for lifecycle segmentation", 5, DATETIME)),
            Map.entry("last_sign_in_at",   new Entry("Useful for activity segmentation", 6, DATETIME)),
            Map.entry("last_login_at",     new Entry("Useful for activity segmentation", 6, DATETIME)),
            Map.entry("is_verified",       new Entry("Useful for targeting verified users", 7, BOOLEAN)),
            Map.entry("email_verified",    new Entry("Useful for targeting verified users", 7, BOOLEAN))
    );

    public static Recommendation recommend(String columnName, String dataType) {
        Entry entry = DICTIONARY.get(columnName.toLowerCase());
        if (entry == null || !entry.validDataTypes().contains(dataType)) {
            return Recommendation.NONE;
        }
        return new Recommendation(true, entry.reason(), entry.rank());
    }
}
