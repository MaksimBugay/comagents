package com.comagents.agentproxy;

import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class AgentControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void returnsAgentInfo() {
        AgentInfoResponse info = client.toBlocking().retrieve("/info", AgentInfoResponse.class);

        assertEquals(AiHarnessProvider.CLAUDE_CODE, info.harnessProvider());
        // answers no start request: a new random UUID
        assertEquals(info.sequenceId(), UUID.fromString(info.sequenceId()).toString());
    }

    @Test
    void exposesKubernetesProbes() {
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/liveness").getStatus());
        assertEquals(HttpStatus.OK, client.toBlocking().exchange("/health/readiness").getStatus());
    }
}
