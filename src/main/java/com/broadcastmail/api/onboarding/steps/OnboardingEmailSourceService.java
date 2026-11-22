package com.broadcastmail.api.onboarding.steps;

import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.SessionReaderService;
import com.broadcastmail.api.connection.schema.EmailSourceService;
import com.broadcastmail.api.connection.dto.EmailSourceOption;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.dto.ConfirmEmailTableRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingEmailSourceService {

    private final OAuthSessionStore onboardingSessionStore;
    private final EmailSourceService emailSourceService;
    private final SessionReaderService sessionReaderService;

    public EmailSourceResult detect(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireEmailTableSource();
        SupabaseIntrospectionData data = session.getIntrospectionData();
        if (data == null) return new EmailSourceResult.NotDetected();

        EmailSourceResult result = emailSourceService.detect(data);
        OnboardingSession updated = switch (result) {
            case EmailSourceResult.Detected(var option, var emailColumn) ->
                    session.withEmailTableDetails(new OnboardingSession.EmailTableDetails(
                            option.tableSchema(), option.tableName(),
                            option.primaryKeyColumn(), emailColumn))
                            .withEmailSourceCandidates(null);
            case EmailSourceResult.MultipleCandidates(List<EmailSourceOption> candidates) ->
                    session.withEmailTableDetails(null).withEmailSourceCandidates(candidates);
            case EmailSourceResult.NotDetected _ ->
                    session.withEmailTableDetails(null).withEmailSourceCandidates(null);
        };
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
    }

    public void confirm(String sessionToken, ConfirmEmailTableRequest request) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireEmailTableSource();
        OnboardingSession updated = request != null
                ? session.withEmailTableDetails(new OnboardingSession.EmailTableDetails(
                        request.emailTableSchema(), request.emailTableName(),
                        request.primaryKeyColumn(), request.emailColumn()))
                : session;
        updated = updated.withEmailTableConfirmed(true);
        onboardingSessionStore.updateSession(sessionToken, updated);
        // The role was provisioned at /schema/test with the profile table only — grant the email table now.
        sessionReaderService.grantSelected(updated);
    }
}
