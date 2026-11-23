package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentorchestrator.controller.AgentSessionResponseRegistry;
import com.comagents.agentorchestrator.controller.RunningAgentSessionRegistry;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class AgentSessionResponseListenerTest {

    @Inject
    RunningAgentSessionRegistry runningSessions;

    @Inject
    AgentSessionResponseRegistry responses;

    // the context's Pushca: the orchestrator's connection, opened on startup
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    @Test
    void aSessionsResponseEndsItsRunAndIsKept() {
        UUID sessionId = UUID.randomUUID();
        runningSessions.append(sessionId, new AgentSessionAttributes(sessionId, null, "sequence-1",
                "orchestrator-own", null, "executor-1", null, "Maks", null, 1_000));
        AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(sessionId,
                AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-own", "Maks"), null, 2_000, "executor-1");

        // as an executor's AgentResponseBroadcast sends it
        connectionFactory.connections.getFirst().receive(
                ComagentsMessage.ofPayload("sequence-1", response, AgentSessionResponse.class).toString());

        await("handled on a thread of its own").atMost(Duration.ofSeconds(5))
                .until(() -> responses.get(sessionId).isPresent());
        assertEquals(Optional.of(response), responses.get(sessionId));
        assertEquals(Optional.empty(), runningSessions.get(sessionId), "the session no longer runs");
    }
}
