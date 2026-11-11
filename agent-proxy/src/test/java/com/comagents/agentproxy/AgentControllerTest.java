package com.comagents.agentproxy;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class AgentControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void returnsAgentInfo() {
        AgentInfo info = client.toBlocking().retrieve("/code_review/info", AgentInfo.class);

        assertEquals("code_review", info.name());
        assertEquals("agent-proxy", info.proxy());
    }

    @Test
    void exposesKubernetesProbes() {
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/liveness").getStatus());
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/readiness").getStatus());
    }

    @Test
    void startRejectsUnknownAgentInPath() {
        assertEquals(HttpStatus.NOT_FOUND, startStatus("/unknown_agent/info", Map.of("agent", "CODE_REVIEW")));
    }

    @Test
    void startRejectsMissingAgentInRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/info", Map.of()));
    }

    @Test
    void startRejectsUnknownAgentInRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/info", Map.of("agent", "UNKNOWN_AGENT")));
    }

    @Test
    void startRejectsDifferentAgentsInPathAndRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus("/code_review/info", Map.of("agent", "IMAGE_GENERATOR")));
    }

    @Test
    void startReportsAgentWithoutSetupService() {
        // IMAGE_GENERATOR runs on CODEX, which has no AgentSetupService yet
        assertEquals(HttpStatus.NOT_IMPLEMENTED, startStatus("/image_generator/info", Map.of("agent", "IMAGE_GENERATOR")));
    }

    private HttpStatus startStatus(String uri, Map<String, Object> body) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST(uri, body)));
        return e.getStatus();
    }
}
