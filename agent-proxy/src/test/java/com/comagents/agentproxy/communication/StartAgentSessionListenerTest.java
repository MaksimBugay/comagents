package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.agent_setup.NoAgentSetupServiceException;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// a session that starts needs the comagents sources from GitHub, so only requests that fail before are sent here
@MicronautTest
class StartAgentSessionListenerTest {

    private final FakePushcaConnector connector = new FakePushcaConnector();

    @Inject
    StartAgentSessionListener listener;

    @Inject
    SessionFolders sessionFolders;

    @MockBean(PushcaConnector.class)
    PushcaConnector pushcaConnector() {
        return connector;
    }

    @Test
    void rejectsAnInvalidRequest() {
        assertThrows(ConstraintViolationException.class, () -> listener.consumePayload("sequence-1",
                new StartAgentSessionRequest(UUID.randomUUID(), AiAgent.CODE_REVIEW, null, "sequence-1", " ", "MBugai")));

        assertNothingDelivered();
    }

    @Test
    void rejectsAnAgentWithoutSetupService() {
        // IMAGE_GENERATOR runs on CODEX, which has no AgentSetupService yet
        assertThrows(NoAgentSetupServiceException.class, () -> listener.consumePayload("sequence-1", new StartAgentSessionRequest(
                UUID.randomUUID(), AiAgent.IMAGE_GENERATOR, null, "sequence-1", "orchestrator-device-1", "MBugai")));

        assertNothingDelivered();
    }

    @Test
    void deliversNothingWhenTheSetupFails() throws IOException {
        UUID sessionId = UUID.randomUUID();
        Files.createDirectories(sessionFolders.of(sessionId));
        try {
            assertThrows(ClaudeSessionExistsException.class, () -> listener.consumePayload("sequence-1", new StartAgentSessionRequest(
                    sessionId, AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai")));

            assertNothingDelivered();
        } finally {
            FileTrees.deleteIfExists(sessionFolders.of(sessionId));
        }
    }

    private void assertNothingDelivered() {
        assertTrue(connector.connections.stream().allMatch(connection -> connection.broadcasts.isEmpty()));
    }
}
