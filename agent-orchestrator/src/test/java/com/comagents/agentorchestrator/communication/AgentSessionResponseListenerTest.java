package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentorchestrator.controller.AgentSessionResponseRegistry;
import com.comagents.agentorchestrator.controller.RunningAgentSessionRegistry;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.SessionCleanupRequest;
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
    void aSessionsResponseEndsItsRunIsKeptAndItsExecutorCleansUp() {
        UUID sessionId = UUID.randomUUID();
        runningSessions.append(sessionId, new AgentSessionAttributes(sessionId, AiAgent.CODE_REVIEW, null, "sequence-1",
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
        // the executor that ran it deletes its files now, not after its cleanup delay
        FakePushcaConnectionFactory.FakeConnection orchestrator = connectionFactory.connections.getFirst();
        await().atMost(Duration.ofSeconds(5)).until(() -> !orchestrator.broadcasts.isEmpty());
        FakePushcaConnectionFactory.Broadcast sent = orchestrator.broadcasts.getLast();
        assertEquals("executor-1", sent.receivers().deviceId);
        assertEquals("CLAUDE_CODE_EXECUTOR", sent.receivers().applicationId);
        SessionCleanupRequest cleanup = (SessionCleanupRequest) ComagentsMessage.fromComagentsMessageString(sent.message())
                .comagentsMessagePayload().payload();
        assertEquals(AiAgent.CODE_REVIEW, cleanup.agent());
        assertEquals(sessionId, cleanup.sessionId());
    }
}
