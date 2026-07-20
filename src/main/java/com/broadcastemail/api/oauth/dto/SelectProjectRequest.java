package com.broadcastemail.api.oauth.dto;

public record SelectProjectRequest( String projectRef,
                                    String partialSessionToken) {
}
