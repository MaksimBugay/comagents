package com.comagents.agentproxy;

import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class AgentControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    SessionFolders sessionFolders;

    @Test
    void returnsAgentInfo() {
        AgentInfo info = client.toBlocking().retrieve("/code_review/info", AgentInfo.class);

        assertEquals("code_review", info.name());
    }

    @Test
    void exposesKubernetesProbes() {
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/liveness").getStatus());
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/readiness").getStatus());
    }

    @Test
    void startRejectsUnknownAgentInPath() {
        assertEquals(HttpStatus.NOT_FOUND, startStatus("/unknown_agent/start",
                Map.of("agent", "CODE_REVIEW", "orchestratorDeviceId", "orchestrator-device-1")));
    }

    @Test
    void startRejectsMissingAgentInRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/start", Map.of()));
    }

    @Test
    void startRejectsMissingOrchestratorDeviceId() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/start", Map.of("agent", "CODE_REVIEW")));
        assertEquals(HttpStatus.BAD_REQUEST,
                startStatus("/code_review/start", Map.of("agent", "CODE_REVIEW", "orchestratorDeviceId", " ")));
    }

    @Test
    void startExplainsWhyTheAgentContextIsInvalid() {
        assertEquals("Not a GitHub repository URL: https://gitlab.com/acme/b.git", startError(
                "[{\"type\": \"GITHUB_REPOSITORY\", \"value\": \"https://gitlab.com/acme/b.git\"}]"));
        assertEquals("Duplicate item STRING focus", startError("""
                [{"type": "STRING", "name": "focus", "value": "security"},
                 {"type": "STRING", "name": "focus", "value": "performance"}]"""));
    }

    // the 400 message for a start request with these context items
    private String startError(String items) {
        String body = "{\"sessionId\": \"" + UUID.randomUUID() + "\", \"agent\": \"CODE_REVIEW\", "
                + "\"orchestratorDeviceId\": \"orchestrator-device-1\", \"agentContext\": {\"items\": " + items + "}}";
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.POST("/code_review/start", body).contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        return e.getResponse().getBody(Map.class).map(json -> String.valueOf(json.get("message"))).orElse("");
    }

    @Test
    void startRejectsUnknownAgentInRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/start",
                Map.of("agent", "UNKNOWN_AGENT", "orchestratorDeviceId", "orchestrator-device-1")));
    }

    @Test
    void startRejectsDifferentAgentsInPathAndRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/start",
                Map.of("agent", "IMAGE_GENERATOR", "orchestratorDeviceId", "orchestrator-device-1")));
    }

    @Test
    void startRejectsSessionIdWithDataOnDisk() throws IOException {
        UUID sessionId = UUID.randomUUID();
        Path earlierSessionsFile = Files.createDirectories(sessionFolders.of(sessionId)).resolve("earlier-run.txt");
        Files.writeString(earlierSessionsFile, "kept");
        try {
            assertEquals(HttpStatus.CONFLICT, startStatus("/code_review/start",
                    Map.of("sessionId", sessionId.toString(), "agent", "CODE_REVIEW", "orchestratorDeviceId", "orchestrator-device-1")));
            assertTrue(Files.exists(earlierSessionsFile), "the earlier session's data is not deleted");
        } finally {
            FileTrees.deleteIfExists(sessionFolders.of(sessionId));
        }
    }

    @Test
    void startReportsAgentWithoutSetupService() {
        // IMAGE_GENERATOR runs on CODEX, which has no AgentSetupService yet
        assertEquals(HttpStatus.NOT_IMPLEMENTED, startStatus("/image_generator/start",
                Map.of("agent", "IMAGE_GENERATOR", "orchestratorDeviceId", "orchestrator-device-1")));
    }

    private HttpStatus startStatus(String uri, Map<String, Object> body) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST(uri, body)));
        return e.getStatus();
    }
}
