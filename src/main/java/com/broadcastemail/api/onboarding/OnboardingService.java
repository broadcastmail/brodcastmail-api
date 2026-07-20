package com.broadcastemail.api.onboarding;

import com.broadcastemail.api.account.AccountCreationService;
import com.broadcastemail.api.account.AccountRepository;
import com.broadcastemail.api.connection.ConnectionService;
import com.broadcastemail.api.connection.SchemaIntrospectionService;
import com.broadcastemail.api.connection.dto.SchemaIntrospectionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor

public class OnboardingService {
    private final OnboardingSessionStore onboardingSessionStore;
    private final AccountCreationService accountCreationService;
    private final AccountRepository accountRepository;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final ConnectionService connectionService;

    public String completeOnboarding(String sessionToken){
        OnboardingSession session = onboardingSessionStore.get(sessionToken);
        String apiKey= accountCreationService.createFromOnboarding(session);
        UUID accountId = accountRepository
                .findByEmail(session.getOwnerEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Account not found after creation"))
                .getId();
        SchemaIntrospectionResult schema = schemaIntrospectionService
                .introspect(
                        session.getJdbcUrl(),
                        session.getEncryptedRolePassword()
                );
        connectionService.createConnection(accountId, session, schema);
        return apiKey;
    }
}
