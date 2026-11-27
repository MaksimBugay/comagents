package com.comagents.agentproxy.communication;

import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.SessionCleanupRequest;
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: a started session ends FAILED at once; the cleanup delay is long, so a deletion
// the test sees is the request's
@MicronautTest
@Property(name = "agent-response.cleanup-delay", value = "1h")
@Property(name = "claude.executable", value = "false")
class SessionCleanupListenerTest {

    private final UUID sessionId = UUID.randomUUID();

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    // the context's Pushca: the executor's connection, opened on startup
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    @BeforeEach
    void startSession() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            slot.start(AiAgent.CODE_REVIEW, agentFolder, ClaudeSessionOptions.DEFAULT).awaitFinished(Duration.ofSeconds(20));
        }
    }

    @AfterEach
    void deleteSessionFolder() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void theOrchestratorsRequestDeletesTheSessionFilesAtOnce() {
        receiveCleanupRequest();

        await().atMost(Duration.ofSeconds(10)).until(() -> !Files.exists(sessionFolders.of(sessionId)));
    }

    @Test
    void anArchivedSessionIsKept() {
        registry.archive(sessionId);

        receiveCleanupRequest();

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .until(() -> Files.exists(sessionFolders.of(sessionId)));
        assertTrue(registry.findSession(sessionId).isPresent());
    }

    // as the orchestrator sends it, once it keeps the session's response
    private void receiveCleanupRequest() {
        connectionFactory.connections.getFirst().receive(ComagentsMessage.ofPayload("sequence-9",
                new SessionCleanupRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-9"), SessionCleanupRequest.class)
                .toString());
    }
}
