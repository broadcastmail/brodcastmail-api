package com.broadcastmail.api.connection;

import com.broadcastmail.api.filterablecolumn.ColumnSelection;
import com.broadcastmail.api.filterablecolumn.FilterableColumnService;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private FilterableColumnService filterableColumnService;

    @InjectMocks
    private ConnectionService connectionService;

    @Test
    void shouldSetRequiredNameAndTypeWhenCreatingConnection() {
        // Given
        ConnectionSpec spec = new ConnectionSpec("project-ref", "My Project", "https://project-ref.supabase.co",
                "aws-0-eu-west-1", "broadcastmail_r_abc", "enc-pw", "public", "profiles", "id");

        // When
        connectionService.createConnection(UUID.randomUUID(), spec,
                new EmailSourceSpec("public", "profiles", "id", "email"),
                new ColumnSelection(List.of(), List.of(), List.of()));

        // Then
        ArgumentCaptor<Connection> saved = ArgumentCaptor.forClass(Connection.class);
        verify(connectionRepository).save(saved.capture());
        assertThat(saved.getValue()).extracting("name", "type")
                .containsExactly("My Project", "supabase");
    }
}
