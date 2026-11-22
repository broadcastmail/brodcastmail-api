package com.broadcastmail.api.supabase.reader;

import com.broadcastmail.api.common.exceptions.ReaderNotReadyException;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReaderConnectionVerifierTest {

    private static final class ScriptedVerifier extends ReaderConnectionVerifier {
        final Deque<SQLException> outcomes = new ArrayDeque<>();
        int attempts;

        ScriptedVerifier(SQLException... failures) {
            super(new long[]{0, 0, 0});
            for (SQLException f : failures) outcomes.add(f);
        }

        @Override
        Connection open(ReaderConnectionFactory reader, String password) throws SQLException {
            attempts++;
            SQLException next = outcomes.poll();
            if (next != null) throw next;
            Connection conn = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(conn.createStatement()).thenReturn(statement);
            return conn;
        }
    }

    private static final ReaderConnectionFactory READER = new ReaderConnectionFactory("ref", "aws-0-eu-west-1", "broadcastmail_r_abc");

    private static SQLException authFailed() {
        return new SQLException("password authentication failed", ReaderConnectionVerifier.INVALID_PASSWORD);
    }

    @Test
    void shouldRetryWhilePoolerRejectsNewPassword() {
        // Given
        ScriptedVerifier verifier = new ScriptedVerifier(authFailed(), authFailed());

        // When
        verifier.verify(READER, "pw");

        // Then
        assertThat(verifier.attempts).isEqualTo(3);
    }

    @Test
    void shouldGiveUpAfterBackoffIsExhausted() {
        // Given
        ScriptedVerifier verifier = new ScriptedVerifier(authFailed(), authFailed(), authFailed(), authFailed());

        // Then
        assertThatThrownBy(() -> verifier.verify(READER, "pw"))
                .isInstanceOf(ReaderNotReadyException.class);
        assertThat(verifier.attempts).isEqualTo(4);
    }

    @Test
    void shouldNotRetryOtherErrors() {
        // Given
        ScriptedVerifier verifier = new ScriptedVerifier(new SQLException("tenant not found", "XX000"));

        // Then
        assertThatThrownBy(() -> verifier.verify(READER, "pw"))
                .isInstanceOf(ReaderNotReadyException.class);
        assertThat(verifier.attempts).isEqualTo(1);
    }
}
