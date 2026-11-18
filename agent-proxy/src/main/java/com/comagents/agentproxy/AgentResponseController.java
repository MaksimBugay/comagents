package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_response.AgentResponseNotReadyException;
import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.agentproxy.agent_status.AgentSessionNotFoundException;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.response.AgentResponseUnavailable;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.UUID;

@Controller("/agent-response")
public class AgentResponseController {

    private final AgentResponseService agentResponseService;

    public AgentResponseController(AgentResponseService agentResponseService) {
        this.agentResponseService = agentResponseService;
    }

    /**
     * 200 with the agent's response, or 404 with {@link AgentResponseUnavailable}: with the session's {@code state}
     * when the response isn't ready yet (try again), without one when the session is unknown.
     */
    // reads the response file (blocking I/O), which must not run on the Netty event loop
    @Get("/{agentName}/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentSessionResponse agentResponse(@PathVariable String agentName, @PathVariable UUID sessionId) {
        AiAgent agent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        return agentResponseService.getResponse(agent, sessionId);
    }

    @Error(exception = AgentResponseNotReadyException.class)
    public HttpResponse<AgentResponseUnavailable> responseNotReady(HttpRequest<?> ignoredRequest,
                                                                   AgentResponseNotReadyException e) {
        return HttpResponse.notFound(new AgentResponseUnavailable(e.getMessage(), e.getState()));
    }

    @Error(exception = AgentSessionNotFoundException.class)
    public HttpResponse<AgentResponseUnavailable> sessionNotFound(HttpRequest<?> ignoredRequest,
                                                                  AgentSessionNotFoundException e) {
        return HttpResponse.notFound(new AgentResponseUnavailable(e.getMessage(), null));
    }
}
