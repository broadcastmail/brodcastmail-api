/**
 * Integration tests against local Supabase instance.
 * Prerequisites:
 *   1. Run: supabase start
 *   2. Run setup SQL in Studio (http://127.0.0.1:54323):
 *      - CREATE ROLE broadcastmail_reader NOINHERIT LOGIN PASSWORD 'testpassword123';
 *      - GRANT USAGE ON SCHEMA public TO broadcastmail_reader;
 *      - GRANT USAGE ON SCHEMA auth TO broadcastmail_reader;
 *      - GRANT SELECT ON ALL TABLES IN SCHEMA public TO broadcastmail_reader;
 *      - CREATE VIEW auth.user_emails AS SELECT id, email FROM auth.users;
 *      - GRANT SELECT ON auth.user_emails TO broadcastmail_reader;
 *      - INSERT 3 test users into auth.users and public.profiles (2 free, 1 pro)
 */

package com.broadcastemail.api.campaign.confirm;

import com.broadcastemail.api.TestContainersConfiguration;
import com.broadcastemail.api.campaign.confirm.dto.RecipientRow;
import com.broadcastemail.api.campaign.filter.FilterQuery;
import com.broadcastemail.api.connection.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class RecipientResolutionServiceTest {

    @Autowired
    private RecipientResolutionService service;

    @Value("${test.supabase.jdbc-url}")
    private String jdbcUrl;
    @Value("${test.supabase.role-password}")
    private String rolePassword;

    private Connection connection;

    @BeforeEach
    void setUp() {
        connection = Connection.builder()
                .userIdColumn("id")
                .emailColumn("email")
                .userTableName("profiles")
                .userTableSchema("public")
                .build();
    }

    @Test
    void shouldReturnAllUsersWhenNoFiltersApplied()
    {
        // Given
        FilterQuery filterQuery = new FilterQuery("", List.of());

        // When
        List<RecipientRow> result = service.resolve(jdbcUrl,rolePassword,connection,filterQuery);

        // Then
        assertThat(result)
                .isNotEmpty()
                .allSatisfy(row -> {
                    assertThat(row.userId()).isNotBlank();
                    assertThat(row.email()).isNotBlank();
                });
    }

    @Test
    void shouldReturnOnlyMatchingUsersWhenFiltersApplied() {
        // Given
        FilterQuery filterQuery = new FilterQuery(
                "WHERE \"plan\" = ?",
                List.of("free")
        );

        // When
        List<RecipientRow> result = service.resolve(jdbcUrl, rolePassword, connection, filterQuery);

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeLessThan(3);
    }

    @Test
    void shouldReturnEmptyListWhenNoUsersMatch() {
        // Given
        FilterQuery filterQuery = new FilterQuery(
                "WHERE \"plan\" = ?",
                List.of("enterprise")
        );

        // When
        List<RecipientRow> result = service.resolve(jdbcUrl, rolePassword, connection, filterQuery);

        // Then
        assertThat(result).isEmpty();
    }

}