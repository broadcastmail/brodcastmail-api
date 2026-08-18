package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.account.AccountCreationService;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.ConnectionService;
import com.broadcastmail.api.onboarding.dto.AccountCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final OnboardingSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final ConnectionService connectionService;

    public String completeOnboarding(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken)
                .requireSchemaConfirmed()
                .requireResendDetails();
        AccountCreationResult result = accountCreationService.createFromOnboarding(session);
        connectionService.createConnection(result.accountId(), session);
        return result.rawApiKey();
    }

    public void testConnection(String sessionToken)
    {
        OnboardingSession onboardingSession = onboardingSessionStore
                .get(sessionToken)
                .requireSchemaConfirmed();

        String rolePassword = onboardingSession.getEncryptedRolePassword();
        String jdbcUrl = onboardingSession.getJdbcUrl();
        try
            (Connection connection = DriverManager.getConnection(jdbcUrl,"broadcastmail_reader", rolePassword);
            Statement stmt = connection.createStatement()
            )
        {
            stmt.execute("SELECT 1 FROM auth.user_emails LIMIT 1");
        }


        catch (SQLException error)
        {
            throw new ConnectionNotFoundException();
        }


    }
}
