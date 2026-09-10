package com.ash.resonance.auth;

import java.util.UUID;

/** What the JWT filter puts on the SecurityContext for every authenticated request. */
public record AuthenticatedPrincipal(UUID userId, UUID deviceId) {
}
