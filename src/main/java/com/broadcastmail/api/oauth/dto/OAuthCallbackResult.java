package com.broadcastmail.api.oauth.dto;

import com.broadcastmail.api.supabase.dto.SupabaseProject;

import java.util.List;

public sealed interface OAuthCallbackResult
        permits OAuthCallbackResult.ReturningUser,
        OAuthCallbackResult.NewUserSingleProject,
        OAuthCallbackResult.NewUserMultipleProjects {

    record ReturningUser(String apiKey) implements OAuthCallbackResult {}

    record NewUserSingleProject(String sessionToken) implements OAuthCallbackResult {}

    record NewUserMultipleProjects(
            List<SupabaseProject> projects,
            String partialSessionToken
    ) implements OAuthCallbackResult {}
}
