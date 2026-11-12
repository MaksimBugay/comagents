package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentStatus;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: it exits with an error at once, so the session ends FAILED
@MicronautTest
@Property(name = "claude.executable", value = "false")
class AgentStatusControllerTest {

    private final UUID sessionId = UUID.randomUUID();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @AfterEach
    void cleanUp() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void reportsFinishedSessionAndWhetherResponseIsAvailable() throws Exception {
        Path agentFolder = SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, agentFolder).awaitFinished(Duration.ofSeconds(20));

        AgentStatus status = client.toBlocking().retrieve(uri(), AgentStatus.class);

        assertEquals(AiAgent.CODE_REVIEW, status.aiAgent());
        assertEquals(sessionId, status.sessionId());
        assertEquals(AgentSessionState.FAILED, status.state());
        assertTrue(status.startedAt() > 0);
        assertNotNull(status.finishedAt());
        assertFalse(status.responseAvailable());

        Files.writeString(SessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW), "# Report");
        assertTrue(client.toBlocking().retrieve(uri(), AgentStatus.class).responseAvailable());
    }

    @Test
    void notFoundForUnknownSession() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf(uri()));
    }

    @Test
    void notFoundForUnknownAgent() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/agent-status/unknown_agent/" + sessionId));
    }

    private String uri() {
        return "/agent-status/code_review/" + sessionId;
    }

    private HttpStatus statusOf(String uri) {
        return assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri)).getStatus();
    }
}
