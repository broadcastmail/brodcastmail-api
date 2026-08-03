package com.broadcastmail.api.oauth.dto;

import com.broadcastmail.api.supabase.dto.SupabaseProject;

import java.util.List;

public record OAuthCallbackResult(
        String sessionToken,           // set when single project, null otherwise
        List<SupabaseProject> projects, // set when multiple projects, null otherwise
        String partialSessionToken     // set when multiple projects, null otherwise
) {
    public boolean requiresProjectSelection() {
        return projects != null && !projects.isEmpty();
    }
}
