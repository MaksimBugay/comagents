package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.AgentSessionLoggingLevel;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.StringAgentContextItem;
import com.comagents.agentproxy.request.StartAgentSessionRequest;
import com.comagents.agentproxy.response.AgentSessionResponse;
import com.comagents.agentproxy.response.AgentResponseUnavailable;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: it exits with an error at once, so a started session ends FAILED
@MicronautTest
@Property(name = "agent-response.cleanup-delay", value = "1s")
@Property(name = "claude.executable", value = "false")
class AgentResponseControllerTest {

    private static final AgentContext ALL_GOOD = new AgentContext(List.of(new StringAgentContextItem("response", "# Report\n\nAll good")));

    private final UUID sessionId = UUID.randomUUID();
    private final AgentSessionAttributes attributes = new AgentSessionAttributes(sessionId,
            new AgentContext(List.of(new StringAgentContextItem("focus", "security"))), "MBugai",
            AgentSessionLoggingLevel.INFO, 1_700_000_000_000L);

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    @AfterEach
    void deleteSessionFolder() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void returnsResponseWithItsRequestAndDeletesSessionFilesAfterDelay() throws Exception {
        startSession();
        writeAllGoodResponse();
        long before = Instant.now().toEpochMilli();

        AgentSessionResponse response = client.toBlocking().retrieve(uri(), AgentSessionResponse.class);

        assertEquals(new StartAgentSessionRequest(sessionId, AiAgent.CODE_REVIEW, attributes.agentContext(), "MBugai",
                AgentSessionLoggingLevel.INFO, 1_700_000_000_000L), response.request());
        assertEquals(ALL_GOOD, response.agentContext());
        assertEquals("# Report\n\nAll good", response.agentContext().items().getFirst().getValue());
        assertTrue(response.createdAt() >= before, "built when fetched");

        // a second fetch within the delay still works and does not schedule another cleanup
        assertEquals(ALL_GOOD, client.toBlocking().retrieve(uri(), AgentSessionResponse.class).agentContext());

        await("session files deleted after the cleanup delay").atMost(Duration.ofSeconds(10))
                .until(() -> !Files.exists(sessionFolders.of(sessionId)));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(uri()));
    }

    @Test
    void notFoundWithoutStateForUnknownSession() throws IOException {
        Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        AgentResponseUnavailable body = e.getResponse().getBody(AgentResponseUnavailable.class).orElseThrow();
        assertNull(body.state());
        assertTrue(body.message().contains("session not found"));
        assertTrue(Files.exists(sessionFolders.of(sessionId)), "nothing is cleaned up without a response");
    }

    @Test
    void notFoundWithSessionStateWhenAgentGaveNoResponse() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
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
    void archivedSessionKeepsItsFilesAfterResponseFetch() throws Exception {
        startSession();
        writeAllGoodResponse();
        registry.archive(sessionId);

        client.toBlocking().retrieve(uri(), AgentSessionResponse.class);

        // the cleanup delay (1 s) passes several times; the archived session's files stay
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> Files.exists(sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW)));
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

    // started by a request with the test's attributes; "false" ends it FAILED at once, without a response
    private void startSession() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            slot.start(AiAgent.CODE_REVIEW, agentFolder, new ClaudeSessionOptions(false, "MBugai", attributes))
                    .awaitFinished(Duration.ofSeconds(20));
        }
    }

    private void writeAllGoodResponse() throws IOException {
        Path responseFile = sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(responseFile.getParent());
        Files.write(responseFile, JsonUtility.toJsonAsBytes(ALL_GOOD));
    }

    private HttpStatus statusOf(String uri) {
        return assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(uri)).getStatus();
    }
}
