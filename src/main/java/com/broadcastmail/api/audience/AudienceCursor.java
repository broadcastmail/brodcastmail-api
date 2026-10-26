package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.supabase.SupabaseSql;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AudienceCursor {

    private AudienceCursor() {}

    public record Decoded(UUID id, String sortVal) {}

    public record PageQuery(String whereClause, List<Object> pageParams) {}

    public static PageQuery buildPageQuery(Optional<String> cursor, String baseWhere,
                                           List<Object> filterParams, SupabaseSql.SortSpec sort,
                                           String idRef) {
        List<Object> pageParams = new ArrayList<>(filterParams);
        if (cursor.isEmpty()) return new PageQuery(baseWhere, pageParams);
        Decoded c = decode(cursor.get());
        String fragment = SupabaseSql.buildCursorFragment(sort, idRef);
        String whereClause = baseWhere.isEmpty() ? "WHERE " + fragment : baseWhere + " AND " + fragment;
        pageParams.addAll(SupabaseSql.buildCursorParams(sort.key(), c.sortVal(), c.id()));
        return new PageQuery(whereClause, pageParams);
    }

    public static Decoded decode(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor));
        int sep = raw.indexOf('|');
        return new Decoded(UUID.fromString(raw.substring(0, sep)), raw.substring(sep + 1));
    }

    public static String encode(AudienceListResponse.AudienceRecipient last, String sortKey) {
        String sortVal = switch (sortKey) {
            case "id" -> last.id().toString();
            case "email" -> last.email();
            default -> String.valueOf(last.attributes().get(sortKey));
        };
        return Base64.getUrlEncoder().withoutPadding().encodeToString((last.id() + "|" + sortVal).getBytes());
    }
}
