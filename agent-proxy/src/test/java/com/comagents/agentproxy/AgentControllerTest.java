package com.comagents.agentproxy;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
