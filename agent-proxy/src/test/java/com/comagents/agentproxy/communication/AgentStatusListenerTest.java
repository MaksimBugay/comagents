package com.comagents.agentproxy.communication;

import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.exception.AgentSessionNotFoundException;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.response.AgentStatusResponse;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// "false" stands in for Claude Code: it exits with an error at once, so the session ends FAILED
@MicronautTest
@Property(name = "claude.executable", value = "false")
class AgentStatusListenerTest {

    private final UUID sessionId = UUID.randomUUID();
    private final AgentStatusRequest request = new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1",
            "orchestrator-device-1");

    @Inject
    AgentStatusListener listener;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    // the context's Pushca: records what was sent
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    // the executor's connection, opened on startup, stays; what earlier tests sent is forgotten
    @BeforeEach
    void resetPushca() {
        connectionFactory.reset();
    }

    @AfterEach
    void cleanUp() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void sendsTheStatusToTheOrchestratorDeviceWithTheRequestsSequenceId() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, agentFolder).awaitFinished(Duration.ofSeconds(20));

        listener.consumePayload("sequence-1", request);

        FakePushcaConnectionFactory.Broadcast sent = connectionFactory.connections.getLast().broadcasts.getFirst();
        assertEquals("orchestrator-device-1", sent.receivers().deviceId);
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(sent.message());
        assertEquals("sequence-1", message.id());
        AgentStatusResponse response = (AgentStatusResponse) message.comagentsMessagePayload().payload();
        assertEquals(request, response.request());
        assertEquals(sessionId, response.status().sessionId());
        assertEquals(AgentSessionState.FAILED, response.status().state());
    }

    @Test
    void sendsBackThatTheSessionIsUnknown() {
        listener.consumePayload("sequence-1", request);

        assertEquals(1, connectionFactory.connections.getLast().broadcasts.size());
        FakePushcaConnectionFactory.Broadcast sent = connectionFactory.connections.getLast().broadcasts.getFirst();
        assertEquals("orchestrator-device-1", sent.receivers().deviceId);
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(sent.message());
        assertEquals("sequence-1", message.id());
        // in place of the AgentStatusResponse
        assertEquals(AgentStatusResponse.class.getName(), message.payloadClassName());
        assertNull(message.payloadString());
        assertEquals(AgentSessionNotFoundException.class.getName(), message.error().errorClassName());
        assertEquals("No session " + sessionId + " for agent code_review", message.error().errorMessage());
    }
}
