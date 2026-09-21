package com.broadcastmail.api.supabase;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseSqlTest {

    @Test
    void shouldBuildJoinedCountQuery() {
        String sql = SupabaseSql.buildRecipientCountQuery("public", "profiles", "id");

        assertThat(sql).isEqualTo(
                "SELECT COUNT(*) FROM auth.user_emails ue JOIN \"public\".\"profiles\" p ON p.\"id\" = ue.id");
    }

    @Test
    void shouldRejectSchemaWithSpecialCharacters() {
        assertThatThrownBy(() -> SupabaseSql.buildRecipientCountQuery("public; DROP TABLE users", "profiles", "id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTableWithSpecialCharacters() {
        assertThatThrownBy(() -> SupabaseSql.buildRecipientCountQuery("public", "profiles\"; --", "id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectIdColumnWithSpecialCharacters() {
        assertThatThrownBy(() -> SupabaseSql.buildRecipientCountQuery("public", "profiles", "id; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullIdentifiers() {
        assertThatThrownBy(() -> SupabaseSql.buildRecipientCountQuery(null, "profiles", "id"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
