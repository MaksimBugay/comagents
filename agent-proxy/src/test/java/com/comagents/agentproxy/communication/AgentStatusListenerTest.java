package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_status.AgentSessionNotFoundException;
import com.comagents.agentproxy.communication.pushca.AgentExecutorPushcaClient;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.response.AgentStatusResponse;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: it exits with an error at once, so the session ends FAILED
@MicronautTest
@Property(name = "claude.executable", value = "false")
class AgentStatusListenerTest {

    private final UUID sessionId = UUID.randomUUID();
    private final AgentStatusRequest request = new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1",
            "orchestrator-device-1");
    private final FakePushcaConnector connector = new FakePushcaConnector();

    @Inject
    AgentStatusListener listener;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    @Inject
    AgentExecutorPushcaClient executorClient;

    @MockBean(PushcaConnector.class)
    PushcaConnector pushcaConnector() {
        return connector;
    }

    // the executor's client connected on startup, through another test's connector; closed, the delivery connects
    // through this test's connector
    @BeforeEach
    void closeExecutorClient() {
        executorClient.close();
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

        FakePushcaConnector.Broadcast sent = connector.connections.getLast().broadcasts.getFirst();
        assertEquals("orchestrator-device-1", sent.receivers().deviceId);
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(sent.message());
        assertEquals("sequence-1", message.id());
        AgentStatusResponse response = (AgentStatusResponse) message.comagentsMessagePayload().payload();
        assertEquals(request, response.request());
        assertEquals(sessionId, response.status().sessionId());
        assertEquals(AgentSessionState.FAILED, response.status().state());
    }

    @Test
    void unknownSessionIsRejectedAndNothingSent() {
        assertEquals("No session " + sessionId + " for agent code_review", assertThrows(
                AgentSessionNotFoundException.class, () -> listener.consumePayload("sequence-1", request)).getMessage());

        assertTrue(connector.connections.stream().allMatch(connection -> connection.broadcasts.isEmpty()));
    }
}
