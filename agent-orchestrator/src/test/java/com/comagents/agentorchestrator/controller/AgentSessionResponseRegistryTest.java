package com.comagents.agentorchestrator.controller;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSessionResponseRegistryTest {

    private final AgentSessionResponseRegistry registry = new AgentSessionResponseRegistry();

    @Test
    void keepsAnAppendedResponseUntilRemoved() {
        UUID sessionId = UUID.randomUUID();
        AgentSessionResponse response = response(sessionId);
        registry.append(sessionId, response);
        assertEquals(Optional.of(response), registry.get(sessionId));

        registry.remove(sessionId);
        assertEquals(Optional.empty(), registry.get(sessionId));
    }

    @Test
    void anUnknownSessionHasNoResponse() {
        assertEquals(Optional.empty(), registry.get(UUID.randomUUID()));
    }

    @Test
    void rejectsAResponseWithoutSessionIdOrResponse() {
        assertThrows(NullPointerException.class, () -> registry.append(null, response(UUID.randomUUID())));
        assertThrows(NullPointerException.class, () -> registry.append(UUID.randomUUID(), null));
    }

    private static AgentSessionResponse response(UUID sessionId) {
        return new AgentSessionResponse(new StartAgentSessionRequest(sessionId, AiAgent.CODE_REVIEW, null,
                "sequence-1", "orchestrator-own", "Maks"), null, 1_000, "executor-1");
    }
}
