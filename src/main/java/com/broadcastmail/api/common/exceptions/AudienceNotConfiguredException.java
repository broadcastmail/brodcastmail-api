
package com.broadcastmail.api.common.exceptions;

public class AudienceNotConfiguredException extends RuntimeException {
    public AudienceNotConfiguredException() {
        super("Audience not configured");
    }
}
