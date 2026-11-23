package com.broadcastmail.api.supabase;

import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.campaign.filter.FilterSource;
import org.hibernate.query.SortDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionQueryContextTest {

    private Connection authConnection(String schema, String table, String idColumn) {
        Connection c = mock(Connection.class);
        when(c.getEmailColumn()).thenReturn(null);
        when(c.getUserTableSchema()).thenReturn(schema);
        when(c.getUserTableName()).thenReturn(table);
        when(c.getUserIdColumn()).thenReturn(idColumn);
        return c;
    }

    @Test
    void shouldBuildJoinedCountQueryForAuthEmail() {
        Connection connection = authConnection("public", "profiles", "id");
        String sql = ConnectionQueryContext.from(connection).buildRecipientCountQuery();

        assertThat(sql).contains("_broadcastmail.user_emails").contains("profiles").contains("id");
    }

    @Test
    void shouldRejectSchemaWithSpecialCharacters() {
        Connection connection = authConnection("public; DROP TABLE users", "profiles", "id");
        assertThatThrownBy(() -> ConnectionQueryContext.from(connection).buildRecipientCountQuery())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTableWithSpecialCharacters() {
        Connection connection = authConnection("public", "profiles\"; --", "id");
        assertThatThrownBy(() -> ConnectionQueryContext.from(connection).buildRecipientCountQuery())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectIdColumnWithSpecialCharacters() {
        Connection connection = authConnection("public", "profiles", "id; DROP TABLE users");
        assertThatThrownBy(() -> ConnectionQueryContext.from(connection).buildRecipientCountQuery())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullSchema() {
        Connection connection = authConnection(null, "profiles", "id");
        assertThatThrownBy(() -> ConnectionQueryContext.from(connection).buildRecipientCountQuery())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildEmailTableCountQuery() {
        Connection connection = mock(Connection.class);
        when(connection.getEmailColumn()).thenReturn("contact_email");
        when(connection.getEmailTableSchema()).thenReturn("public");
        when(connection.getEmailTableName()).thenReturn("contacts");

        String sql = ConnectionQueryContext.from(connection).buildRecipientCountQuery();

        assertThat(sql).doesNotContain("_broadcastmail.user_emails").contains("\"public\".\"contacts\"");
    }

    @Test
    void shouldNotJoinEmailTableToItselfWhenItIsAlsoTheProfileTable() {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.getEmailColumn()).thenReturn("email");
        when(connection.getEmailTableSchema()).thenReturn("public");
        when(connection.getEmailTableName()).thenReturn("profiles");
        when(connection.getEmailTableIdColumn()).thenReturn("id");
        when(connection.getUserTableSchema()).thenReturn("public");
        when(connection.getUserTableName()).thenReturn("profiles");
        when(connection.getUserIdColumn()).thenReturn("id");
        ConnectionQueryContext ctx = ConnectionQueryContext.from(connection);

        // When
        String count = ctx.buildRecipientCountQuery();
        String list = ctx.buildAudienceQuery("WHERE \"country\" = ?", "WHERE \"country\" = ?",
                ctx.buildSortSpec(FilterSource.PROFILE_TABLE, "country", SortDirection.ASCENDING));

        // Then
        assertThat(count).doesNotContain("JOIN");
        assertThat(list).doesNotContain("JOIN").contains("email.*").contains("ORDER BY email.\"country\"");
    }
}
