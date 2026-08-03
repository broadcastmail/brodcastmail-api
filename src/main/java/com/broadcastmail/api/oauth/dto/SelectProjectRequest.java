package com.broadcastmail.api.oauth.dto;

public record SelectProjectRequest( String projectRef,
                                    String partialSessionToken) {
}
