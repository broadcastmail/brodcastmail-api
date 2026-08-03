package com.broadcastmail.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class ApiKeyAuthentication implements Authentication {
    private final UUID accountId;
    private boolean authenticated = true;

    public ApiKeyAuthentication(UUID accountId) {
        this.accountId = accountId;
    }

    @Override public UUID getPrincipal() {
        return accountId;
    }
    @Override public boolean isAuthenticated()
    {
        return authenticated;
    }
    @Override public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    @Override public Object getCredentials() { return null; }
    @Override public Object getDetails() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return Collections.emptyList(); }
    @Override public String getName() { return accountId.toString(); }

}
