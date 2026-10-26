package com.broadcastmail.api.audience;

import com.broadcastmail.api.audience.dto.AudienceListResponse;
import com.broadcastmail.api.supabase.SupabaseSql;
import org.hibernate.query.SortDirection;

import java.util.ArrayList;
import java.util.Arrays;
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
        String fragment = buildCursorFragment(sort, idRef);
        String whereClause = baseWhere.isEmpty() ? "WHERE " + fragment : baseWhere + " AND " + fragment;
        pageParams.addAll(buildCursorParams(sort.key(), c.sortVal(), c.id()));
        return new PageQuery(whereClause, pageParams);
    }

    private static String buildCursorFragment(SupabaseSql.SortSpec sort, String idRef) {
        String op = sort.direction() == SortDirection.ASCENDING ? ">" : "<";
        if (idRef.endsWith(".\"" + sort.key() + "\"")) {
            return idRef + " " + op + " ?";
        }
        return "(" + sort.alias() + ".\"" + sort.key() + "\" " + op + " ? OR ("
                + sort.alias() + ".\"" + sort.key() + "\" = ? AND " + idRef + " " + op + " ?))";
    }

    private static List<Object> buildCursorParams(String sortKey, String lastSortVal, UUID lastId) {
        if ("id".equals(sortKey)) {
            return List.of(lastId);
        }
        return Arrays.asList(lastSortVal, lastSortVal, lastId);
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
