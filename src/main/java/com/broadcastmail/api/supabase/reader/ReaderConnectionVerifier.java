package com.broadcastmail.api.supabase.reader;

import com.broadcastmail.api.common.exceptions.ReaderNotReadyException;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Confirms a freshly provisioned reader role can log in through Supavisor. The pooler caches role secrets, so a new
 * role can be rejected with 28P01 for a few seconds — only that error is retried; anything else fails immediately.
 */
@Slf4j
@Component
public class ReaderConnectionVerifier {

    static final String INVALID_PASSWORD = "28P01";
    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final String PROBE_SQL = "SELECT 1 FROM _broadcastmail.user_emails LIMIT 1";

    private final long[] backoffMs;

    public ReaderConnectionVerifier(
            @Value("${broadcastmail.reader.verify-backoff-ms:500,1000,2000,4000,8000}") long[] backoffMs) {
        this.backoffMs = backoffMs;
    }

    public void verify(ReaderConnectionFactory reader, String password) {
        String username = reader.username();
        for (int attempt = 0; ; attempt++) {
            try (Connection conn = open(reader, password);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(PROBE_SQL);
                if (attempt > 0) log.info("[verify] user={} ready after {} retries", username, attempt);
                return;
            } catch (SQLException e) {
                boolean retryable = INVALID_PASSWORD.equals(e.getSQLState()) && attempt < backoffMs.length;
                log.info("[verify] attempt={} user={} sqlState={} retry={} msg={}",
                        attempt, username, e.getSQLState(), retryable, e.getMessage());
                if (!retryable) {
                    throw new ReaderNotReadyException("Could not connect to the Supabase database: " + e.getMessage(), e);
                }
                sleep(backoffMs[attempt]);
            }
        }
    }

    /** Seam for tests; production just delegates to the factory. */
    Connection open(ReaderConnectionFactory reader, String password) throws SQLException {
        return reader.open(password, LOGIN_TIMEOUT_SECONDS);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReaderNotReadyException("Interrupted while verifying connection", e);
        }
    }
}
