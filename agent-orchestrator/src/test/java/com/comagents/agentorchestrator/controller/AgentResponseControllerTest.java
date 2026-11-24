package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.exception.AgentResponseNotReadyException;
import com.comagents.commons.exception.AgentSessionNotFoundException;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentResponseReply;
import com.comagents.commons.response.AgentResponseUnavailable;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MicronautTest
class AgentResponseControllerTest {

    private final UUID sessionId = UUID.randomUUID();
    private final AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(sessionId,
            AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-own", "Maks"), null, 2_000, "claude-1");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ComagentsGateway gateway;

    @Inject
    AgentSessionResponseRegistry responses;

    @Inject
    RunningAgentSessionRegistry runningSessions;

    @MockBean(ComagentsGateway.class)
    ComagentsGateway gateway() {
        return mock(ComagentsGateway.class);
    }

    @Test
    void returnsTheResponseTheExecutorSent() {
        responses.append(response);

        assertEquals(response, fetch());
        verifyNoInteractions(gateway);
    }

    @Test
    void asksTheExecutorWhileNoResponseCame() {
        runningSessions.append(sessionId, running());
        when(gateway.exchange(any(), any(), eq(AgentResponseReply.class))).thenReturn(new AgentResponseReply(response));

        assertEquals(response, fetch());

        ArgumentCaptor<AgentResponseRequest> request = ArgumentCaptor.forClass(AgentResponseRequest.class);
        verify(gateway).exchange(eq(DeliveryAddress.agentExecutor("claude-1", AiHarnessProvider.CLAUDE_CODE)),
                request.capture(), eq(AgentResponseReply.class));
        assertEquals(AiAgent.CODE_REVIEW, request.getValue().agent());
        assertEquals(sessionId, request.getValue().sessionId());
    }

    @Test
    void notFoundWithTheStateWhileTheSessionRuns() {
        runningSessions.append(sessionId, running());
        when(gateway.exchange(any(), any(), eq(AgentResponseReply.class))).thenThrow(
                new AgentResponseNotReadyException(AiAgent.CODE_REVIEW, sessionId, AgentSessionState.RUNNING));

        AgentResponseUnavailable unavailable = notFound();
        assertEquals(AgentSessionState.RUNNING, unavailable.state());
        assertTrue(unavailable.message().contains(sessionId.toString()), unavailable.message());
    }

    @Test
    void notFoundWithoutStateWhenTheExecutorDoesNotKnowTheSession() {
        runningSessions.append(sessionId, running());
        when(gateway.exchange(any(), any(), eq(AgentResponseReply.class)))
                .thenThrow(new AgentSessionNotFoundException(AiAgent.CODE_REVIEW, sessionId));

        assertNull(notFound().state());
    }

    @Test
    void gatewayTimeoutWhenTheExecutorDoesNotReplyInTime() {
        runningSessions.append(sessionId, running());
        when(gateway.exchange(any(), any(), eq(AgentResponseReply.class))).thenThrow(
                new ComagentsResponseTimeoutException("No response to sequence-1 in time", new TimeoutException()));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange("/agent-response/" + sessionId, AgentSessionResponse.class));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, e.getStatus());
    }

    @Test
    void notFoundWithoutStateForAnUnknownSession() {
        AgentResponseUnavailable unavailable = notFound();

        assertNull(unavailable.state());
        assertEquals("No session " + sessionId, unavailable.message());
        verifyNoInteractions(gateway);
    }

    @Test
    void badRequestForAnInvalidSessionId() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange("/agent-response/not-a-uuid"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    private AgentSessionAttributes running() {
        return new AgentSessionAttributes(sessionId, AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-own", null,
                "claude-1", null, "Maks", null, 1_000);
    }

    private AgentSessionResponse fetch() {
        return client.toBlocking().retrieve("/agent-response/" + sessionId, AgentSessionResponse.class);
    }

    private AgentResponseUnavailable notFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange("/agent-response/" + sessionId, AgentSessionResponse.class));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        return e.getResponse().getBody(AgentResponseUnavailable.class).orElseThrow();
    }
}
