package com.broadcastmail.api.oauth.dto;


public sealed interface OAuthCallbackResult
        permits OAuthCallbackResult.ReturningUser, OAuthCallbackResult.NewUserSingleProject, OAuthCallbackResult.NewUserMultipleProjects,
        OAuthCallbackResult.ReconfigureSingleProject, OAuthCallbackResult.ReconfigureMultipleProjects {

    record ReturningUser(String apiKey) implements OAuthCallbackResult {
    }

    record NewUserSingleProject(String sessionToken) implements OAuthCallbackResult {
    }

    record NewUserMultipleProjects(String partialSessionToken) implements OAuthCallbackResult {
    }

    record ReconfigureSingleProject(String sessionToken) implements OAuthCallbackResult {
    }

    record ReconfigureMultipleProjects(String partialSessionToken) implements OAuthCallbackResult {
    }
}
