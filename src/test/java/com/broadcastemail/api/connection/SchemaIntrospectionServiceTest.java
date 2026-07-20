package com.broadcastemail.api.connection;

import com.broadcastemail.api.common.SecurityUtil;
import com.broadcastemail.api.config.EncryptionProperties;
import com.broadcastemail.api.connection.dto.DetectedColumn;
import com.broadcastemail.api.connection.dto.SchemaIntrospectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaIntrospectionServiceTest {

    @Autowired
    private SchemaIntrospectionService schemaIntrospectionService;

    @Autowired
    private EncryptionProperties encryptionProperties;

    @Value("${test.supabase.jdbc-url}")
    private String jdbcUrl;

    private String encryptedRolePassword;

    @BeforeEach
    void setUp() {
        encryptedRolePassword = SecurityUtil.encrypt("testpassword123", encryptionProperties.key());
    }

    @Test
    void shouldDetectAuthUsersTable() {
        // Given / When
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(jdbcUrl, encryptedRolePassword);

        // Then
        assertThat(result.userTableName()).isEqualTo("users");
        assertThat(result.userTableSchema()).isEqualTo("auth");
        assertThat(result.emailColumn()).isEqualTo("email");
        assertThat(result.userIdColumn()).isEqualTo("id");
    }

    @Test
    void shouldDetectProfilesTableIfExists() {
        // Given / When
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(jdbcUrl, encryptedRolePassword);

        // Then
        assertThat(result.filterableColumns())
                .extracting(DetectedColumn::columnName)
                .contains("full_name", "plan", "created_at");
    }

    @Test
    void shouldExcludeNonFilterableColumns() {
        // Given / When
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(jdbcUrl, encryptedRolePassword);

        // Then
        assertThat(result.filterableColumns()).isNotEmpty();
        assertThat(result.filterableColumns())
                .extracting(DetectedColumn::columnName)
                .doesNotContain("id", "email", "avatar_url");
    }

    @Test
    void shouldFlagHighCardinalityColumns() {
        // Given / When
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(jdbcUrl, encryptedRolePassword);

        // Then
        assertThat(result.filterableColumns())
                .filteredOn(col -> col.columnName().equals("full_name"))
                .first()
                .extracting(DetectedColumn::cardinalityWarning)
                .isEqualTo(false);
    }

    @Test
    void shouldPreTickLowCardinalityColumns() {
        // Given / When
        SchemaIntrospectionResult result = schemaIntrospectionService.introspect(jdbcUrl, encryptedRolePassword);

        // Then
        assertThat(result.filterableColumns())
                .filteredOn(col -> col.columnName().equals("plan"))
                .first()
                .extracting(DetectedColumn::enabled)
                .isEqualTo(true);
    }
}