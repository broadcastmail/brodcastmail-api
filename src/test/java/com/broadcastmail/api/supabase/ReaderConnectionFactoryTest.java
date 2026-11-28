package com.broadcastmail.api.supabase;

import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lives here because broadcastmail-common has no test setup. */
class ReaderConnectionFactoryTest {

    @Test
    void shouldBuildPoolerUrlAndTenantSuffixedUsername() {
        // When
        ReaderConnectionFactory reader = new ReaderConnectionFactory("ref123", "aws-0-eu-west-1", "batchbeam_r_abc");

        // Then
        assertThat(reader.jdbcUrl())
                .isEqualTo("jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?prepareThreshold=0&stringtype=unspecified");
        assertThat(reader.username()).isEqualTo("batchbeam_r_abc.ref123");
    }

    @Test
    void shouldRejectMissingParts() {
        assertThatThrownBy(() -> new ReaderConnectionFactory("ref", null, "role")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReaderConnectionFactory("ref", "prefix", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionWithoutPoolerPrefixShouldFailClearly() {
        // Given — row persisted without a pooler prefix
        Connection connection = Connection.builder().projectRef("ref").build();

        // Then
        assertThatThrownBy(connection::readerConnectionFactory)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no pooler prefix");
    }

    @Test
    void connectionShouldUseItsOwnReaderRole() {
        // Given
        Connection connection = Connection.builder()
                .projectRef("ref")
                .poolerPrefix("aws-0-eu-west-1")
                .dbRole("batchbeam_r_abc")
                .build();

        // Then
        assertThat(connection.readerConnectionFactory().username()).isEqualTo("batchbeam_r_abc.ref");
    }
}
