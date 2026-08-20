package com.broadcastmail.api.oauth.dto;


public sealed interface OAuthCallbackResult
        permits OAuthCallbackResult.ReturningUser,
        OAuthCallbackResult.NewUserSingleProject,
        OAuthCallbackResult.NewUserMultipleProjects {

    record ReturningUser(String apiKey) implements OAuthCallbackResult {}

    record NewUserSingleProject(String sessionToken) implements OAuthCallbackResult {}

    record NewUserMultipleProjects(
            String partialSessionToken
    ) implements OAuthCallbackResult {}
}
