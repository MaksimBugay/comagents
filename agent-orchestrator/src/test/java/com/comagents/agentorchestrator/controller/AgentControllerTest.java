package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.InvalidAgentContextException;
import com.comagents.commons.exception.ClaudeSessionExistsException;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.commons.exception.NoAgentSetupServiceException;
import com.comagents.commons.response.AgentInfoResponse;
import com.comagents.commons.response.StartAgentSessionExternalResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static com.comagents.commons.response.AgentInfoResponse.AGENT_DISCOVERY_SEQUENCE_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
class AgentControllerTest {

    private static final Map<String, Object> START = Map.of("agent", "CODE_REVIEW", "actorAccountId", "Maks");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ComagentsGateway gateway;

    @Inject
    AgentDiscoveryRegistry discoveryRegistry;

    @Inject
    RunningAgentSessionRegistry runningSessions;

    @MockBean(ComagentsGateway.class)
    ComagentsGateway gateway() {
        return mock(ComagentsGateway.class);
    }

    // an executor refusing a session for lack of slots is forgotten: each test has it back
    @BeforeEach
    void discoverAnExecutor() {
        discoveryRegistry.update(new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-1", 3, 3, 0, 0,
                AGENT_DISCOVERY_SEQUENCE_ID, System.currentTimeMillis()));
    }

    @Test
    void startsEverySessionWithANewId() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class))).thenReturn(
                new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-1", 3, 3, 0, 0, "sequence-1", 0));

        HttpResponse<StartAgentSessionExternalResponse> first = start();
        HttpResponse<StartAgentSessionExternalResponse> second = start();

        assertEquals(HttpStatus.ACCEPTED, first.getStatus());
        assertNotNull(first.body());
        assertNotNull(second.body());
        UUID sessionId = first.body().sessionId();
        assertNotEquals(sessionId, second.body().sessionId());
        assertEquals("claude-1", runningSessions.get(sessionId).orElseThrow().agentExecutorDeviceId());
        assertEquals(AiAgent.CODE_REVIEW, runningSessions.get(sessionId).orElseThrow().agent());
    }

    @Test
    void tooManyRequestsWhenTheExecutorIsFull() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class)))
                .thenThrow(new ClaudeSessionLimitException(3));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, startStatus(START));
    }

    @Test
    void tooManyRequestsWithoutAFreeExecutor() {
        discoveryRegistry.remove("claude-1");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, startStatus(START));
    }

    @Test
    void gatewayTimeoutWhenTheExecutorDoesNotReplyInTime() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class))).thenThrow(
                new ComagentsResponseTimeoutException("No response to sequence-1 in time", new TimeoutException()));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, startStatus(START));
    }

    @Test
    void notImplementedForAnAgentWithoutSetupService() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class)))
                .thenThrow(new NoAgentSetupServiceException(AiAgent.CODE_REVIEW));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, startStatus(START));
    }

    @Test
    void conflictForASessionIdInUse() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class)))
                .thenThrow(new ClaudeSessionExistsException(UUID.randomUUID(), "is in use"));

        assertEquals(HttpStatus.CONFLICT, startStatus(START));
    }

    @Test
    void badRequestForAnAgentContextTheExecutorRejects() {
        when(gateway.exchange(any(), any(), eq(AgentInfoResponse.class)))
                .thenThrow(new InvalidAgentContextException("Required repository is missing"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/start", START)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("Required repository is missing",
                e.getResponse().getBody(Map.class).map(json -> String.valueOf(json.get("message"))).orElse(""));
    }

    @Test
    void rejectsMissingAgent() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus(Map.of("actorAccountId", "Maks")));
    }

    @Test
    void rejectsUnknownAgent() {
        assertEquals(HttpStatus.BAD_REQUEST, startStatus(Map.of("agent", "UNKNOWN_AGENT")));
    }

    @Test
    void explainsWhyTheAgentContextIsInvalid() {
        assertEquals("Not a GitHub repository URL: https://gitlab.com/acme/b.git", startError(
                "[{\"type\": \"GITHUB_REPOSITORY\", \"value\": \"https://gitlab.com/acme/b.git\"}]"));
        assertEquals("Duplicate item STRING focus", startError("""
                [{"type": "STRING", "name": "focus", "value": "security"},
                 {"type": "STRING", "name": "focus", "value": "performance"}]"""));
    }

    private HttpResponse<StartAgentSessionExternalResponse> start() {
        return client.toBlocking().exchange(HttpRequest.POST("/start", START), StartAgentSessionExternalResponse.class);
    }

    // the 400 message for a start request with these context items
    private String startError(String items) {
        String body = "{\"agent\": \"CODE_REVIEW\", \"agentContext\": {\"items\": " + items + "}}";
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.POST("/start", body).contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        return e.getResponse().getBody(Map.class).map(json -> String.valueOf(json.get("message"))).orElse("");
    }

    private HttpStatus startStatus(Map<String, Object> body) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/start", body)));
        return e.getStatus();
    }
}
