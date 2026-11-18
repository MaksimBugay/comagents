package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.agentproxy.agent_response.AgentResponseUnavailableException;
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
     * 200 with the agent's response, or 404 with {@link AgentResponseUnavailable}, whose {@code state} tells the
     * caller whether to try again ({@code RUNNING}) or not.
     */
    // reads the response file (blocking I/O), which must not run on the Netty event loop
    @Get("/{agentName}/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentSessionResponse agentResponse(@PathVariable String agentName, @PathVariable UUID sessionId) {
        AiAgent agent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        return agentResponseService.getResponse(agent, sessionId);
    }

    @Error(exception = AgentResponseUnavailableException.class)
    public HttpResponse<AgentResponseUnavailable> responseUnavailable(HttpRequest<?> ignoredRequest,
                                                                      AgentResponseUnavailableException e) {
        return HttpResponse.notFound(e.getUnavailable());
    }
}
