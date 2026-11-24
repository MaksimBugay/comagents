package com.comagents.agentorchestrator.controller;

import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AiAgent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunningAgentSessionRegistryTest {

    private final RunningAgentSessionRegistry registry = new RunningAgentSessionRegistry(Duration.ofMinutes(1));

    @Test
    void keepsAnAppendedSessionUntilRemoved() {
        AgentSessionAttributes attributes = attributes();
        registry.append(attributes.sessionId(), attributes);
        assertEquals(Optional.of(attributes), registry.get(attributes.sessionId()));

        registry.remove(attributes.sessionId());
        assertEquals(Optional.empty(), registry.get(attributes.sessionId()));
    }

    @Test
    void anUnknownSessionIsEmpty() {
        assertEquals(Optional.empty(), registry.get(UUID.randomUUID()));
    }

    @Test
    void forgetsSessionsAfterTheTtl() {
        RunningAgentSessionRegistry shortLived = new RunningAgentSessionRegistry(Duration.ofMillis(200));
        AgentSessionAttributes attributes = attributes();
        shortLived.append(attributes.sessionId(), attributes);
        assertEquals(Optional.of(attributes), shortLived.get(attributes.sessionId()));

        await().atMost(Duration.ofSeconds(5)).until(() -> shortLived.get(attributes.sessionId()).isEmpty());
    }

    @Test
    void rejectsASessionWithoutIdOrAttributes() {
        assertThrows(NullPointerException.class, () -> registry.append(null, attributes()));
        assertThrows(NullPointerException.class, () -> registry.append(UUID.randomUUID(), null));
    }

    private static AgentSessionAttributes attributes() {
        return new AgentSessionAttributes(UUID.randomUUID(), AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-own", null, "executor-1",
                null, "Maks", null, 1_000);
    }
}
