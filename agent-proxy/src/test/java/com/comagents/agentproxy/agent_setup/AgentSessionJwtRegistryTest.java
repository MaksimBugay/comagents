package com.comagents.agentproxy.agent_setup;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSessionJwtRegistryTest {

    private final AgentSessionJwtRegistry registry = new AgentSessionJwtRegistry();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void getsTheStoredJwt() {
        registry.store(sessionId, "jwt-1");

        assertEquals(Optional.of("jwt-1"), registry.get(sessionId));
    }

    @Test
    void aNewJwtReplacesTheOldOne() {
        registry.store(sessionId, "jwt-1");
        registry.store(sessionId, "jwt-2");

        assertEquals(Optional.of("jwt-2"), registry.get(sessionId));
    }

    @Test
    void noJwtForAnUnknownSession() {
        assertEquals(Optional.empty(), registry.get(sessionId));
    }

    @Test
    void aRemovedJwtIsGone() {
        registry.store(sessionId, "jwt-1");

        registry.remove(sessionId);

        assertEquals(Optional.empty(), registry.get(sessionId));
    }

    @Test
    void removingAnUnknownSessionDoesNothing() {
        registry.remove(sessionId);

        assertEquals(Optional.empty(), registry.get(sessionId));
    }

    @Test
    void aBlankJwtIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.store(sessionId, " "));
    }
}
