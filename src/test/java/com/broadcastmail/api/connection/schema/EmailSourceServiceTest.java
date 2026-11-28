package com.broadcastmail.api.connection.schema;

import com.broadcastmail.api.connection.dto.EmailSourceOption;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSourceServiceTest {

    private final EmailSourceService service = new EmailSourceService(null, null, null);

    @Test
    void shouldDetectSignUpAddressWhenNoTableHasAnEmailColumn() {
        // Given
        var data = introspection(Map.of());

        // When
        var result = service.detect(data);

        // Then
        assertThat(result).isInstanceOf(EmailSourceResult.Detected.class);
        assertThat(((EmailSourceResult.Detected) result).option()).isEqualTo(EmailSourceOption.AUTH_USERS);
    }

    @Test
    void shouldOfferSignUpAddressFirstAlongsideTableColumns() {
        // Given
        var data = introspection(Map.of("public.profiles", List.of("email")));

        // When
        var result = service.detect(data);

        // Then
        assertThat(result).isInstanceOf(EmailSourceResult.MultipleCandidates.class);
        var candidates = ((EmailSourceResult.MultipleCandidates) result).candidates();
        assertThat(candidates).first().isEqualTo(EmailSourceOption.AUTH_USERS);
        assertThat(candidates).extracting(EmailSourceOption::tableName).containsExactly("users", "profiles");
    }

    @Test
    void shouldListTablesWithTheirPrimaryKeyAndTextColumns() {
        // Given
        var columns = Map.of(
                "public.profiles", List.of(new SupabaseIntrospectionData.Column("id", "uuid"),
                        new SupabaseIntrospectionData.Column("contact", "text"),
                        new SupabaseIntrospectionData.Column("plan", "character varying(20)")),
                "public.events", List.of(new SupabaseIntrospectionData.Column("note", "text")),
                "auth.users", List.of(new SupabaseIntrospectionData.Column("email", "character varying")));
        var data = new SupabaseIntrospectionData(List.of(), columns, Map.of("public.profiles", "id"), Map.of(), Map.of());

        // When
        var result = (EmailSourceResult.Detected) service.detect(data);

        // Then
        assertThat(result.tables()).containsExactly(
                new EmailSourceOption("public", "profiles", "id", List.of("contact", "plan")));
    }

    private static SupabaseIntrospectionData introspection(Map<String, List<String>> emailColumnsByTable) {
        return new SupabaseIntrospectionData(List.of(), Map.of(), Map.of("public.profiles", "id"), Map.of(),
                emailColumnsByTable);
    }
}
