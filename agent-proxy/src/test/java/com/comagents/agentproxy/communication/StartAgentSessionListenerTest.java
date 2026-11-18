package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.agent_setup.NoAgentSetupServiceException;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.communication.pushca.AgentExecutorPushcaClient;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfo;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// a session that starts needs the comagents sources from GitHub, so only requests that fail before are sent here
@MicronautTest
class StartAgentSessionListenerTest {

    private final FakePushcaConnector connector = new FakePushcaConnector();

    @Inject
    StartAgentSessionListener listener;

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

    @Test
    void sendsBackWhyAnInvalidRequestIsRejected() {
        listener.consumePayload("sequence-1",
                new StartAgentSessionRequest(UUID.randomUUID(), null, null, "sequence-1", "orchestrator-device-1", "MBugai"));

        assertEquals(ConstraintViolationException.class.getName(), sentError().error().errorClassName());
    }

    @Test
    void sendsBackThatTheAgentHasNoSetupService() {
        // IMAGE_GENERATOR runs on CODEX, which has no AgentSetupService yet
        listener.consumePayload("sequence-1", new StartAgentSessionRequest(
                UUID.randomUUID(), AiAgent.IMAGE_GENERATOR, null, "sequence-1", "orchestrator-device-1", "MBugai"));

        ComagentsMessage error = sentError();
        assertEquals(NoAgentSetupServiceException.class.getName(), error.error().errorClassName());
        assertEquals("No setup service for harness provider CODEX (agent image_generator)", error.error().errorMessage());
    }

    @Test
    void sendsBackWhyTheSetupFailed() throws IOException {
        UUID sessionId = UUID.randomUUID();
        Files.createDirectories(sessionFolders.of(sessionId));
        try {
            listener.consumePayload("sequence-1", new StartAgentSessionRequest(
                    sessionId, AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"));

            assertEquals(ClaudeSessionExistsException.class.getName(), sentError().error().errorClassName());
        } finally {
            FileTrees.deleteIfExists(sessionFolders.of(sessionId));
        }
    }

    // the only message sent: an error in place of the AgentInfo, to the requesting device, with the request's id
    private ComagentsMessage sentError() {
        FakePushcaConnector.Broadcast sent = connector.connections.getLast().broadcasts.getFirst();
        assertEquals(1, connector.connections.getLast().broadcasts.size());
        assertEquals("orchestrator-device-1", sent.receivers().deviceId);
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(sent.message());
        assertEquals("sequence-1", message.id());
        assertEquals(AgentInfo.class.getName(), message.payloadClassName());
        assertNull(message.payloadString());
        return message;
    }
}
