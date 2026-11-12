package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentResponse;
import com.comagents.agentproxy.response.AgentResponseUnavailable;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: it exits with an error at once, so a started session ends FAILED
@MicronautTest
@Property(name = "agent-response.cleanup-delay", value = "1s")
@Property(name = "claude.executable", value = "false")
class AgentResponseControllerTest {

    private final UUID sessionId = UUID.randomUUID();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @AfterEach
    void deleteSessionFolder() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void returnsResponseAndDeletesSessionFilesAfterDelay() throws Exception {
        Path responseFile = writeResponse("# Report\n\nAll good");
        long createdAt = Files.readAttributes(responseFile, java.nio.file.attribute.BasicFileAttributes.class).creationTime().toMillis();

        AgentResponse response = client.toBlocking().retrieve(uri(), AgentResponse.class);

        assertEquals(AiAgent.CODE_REVIEW, response.aiAgent());
        assertEquals(sessionId, response.sessionId());
        assertArrayEquals("# Report\n\nAll good".getBytes(StandardCharsets.UTF_8), response.body());
        assertEquals(createdAt, response.createdAt());

        // a second fetch within the delay still works and does not schedule another cleanup
        assertArrayEquals(response.body(), client.toBlocking().retrieve(uri(), AgentResponse.class).body());

        awaitDeleted(SessionFolders.of(sessionId), Duration.ofSeconds(10));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(uri()));
    }

    @Test
    void notFoundWithoutStateForUnknownSession() throws IOException {
        Files.createDirectories(SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        AgentResponseUnavailable body = e.getResponse().getBody(AgentResponseUnavailable.class).orElseThrow();
        assertNull(body.state());
        assertTrue(body.message().contains("session not found"));
        assertTrue(Files.exists(SessionFolders.of(sessionId)), "nothing is cleaned up without a response");
    }

    @Test
    void notFoundWithSessionStateWhenAgentGaveNoResponse() throws Exception {
        Path agentFolder = SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, agentFolder).awaitFinished(Duration.ofSeconds(20));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        AgentResponseUnavailable body = e.getResponse().getBody(AgentResponseUnavailable.class).orElseThrow();
        assertEquals(AgentSessionState.FAILED, body.state());
        assertTrue(body.message().endsWith("session is FAILED"));
    }

    @Test
    void notFoundForUnknownAgent() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/agent-response/unknown_agent/" + sessionId));
    }

    @Test
    void badRequestForInvalidSessionId() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf("/agent-response/code_review/not-a-uuid"));
    }

    private String uri() {
        return "/agent-response/code_review/" + sessionId;
    }

    private Path writeResponse(String text) throws IOException {
        Path responseFile = SessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(responseFile.getParent());
        return Files.writeString(responseFile, text);
    }

    private HttpStatus statusOf(String uri) {
        return assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri)).getStatus();
    }

    private static void awaitDeleted(Path path, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Files.exists(path) && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertFalse(Files.exists(path), path + " was not deleted within " + timeout);
    }
}
