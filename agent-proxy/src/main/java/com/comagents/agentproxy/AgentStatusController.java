package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_status.AgentSessionNotFoundException;
import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.response.AgentStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.UUID;

@Controller("/agent-status")
public class AgentStatusController {

    private final AgentStatusService agentStatusService;

    public AgentStatusController(AgentStatusService agentStatusService) {
        this.agentStatusService = agentStatusService;
    }

    // checks for the response file (blocking I/O), which must not run on the Netty event loop
    @Get("/{agentName}/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentStatus agentStatus(@PathVariable String agentName, @PathVariable UUID sessionId) {
        AiAgent agent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        return agentStatusService.getStatus(agent, sessionId);
    }

    @Error(exception = AgentSessionNotFoundException.class)
    public HttpResponse<JsonError> sessionNotFound(HttpRequest<?> ignoredRequest, AgentSessionNotFoundException e) {
        return HttpResponse.<JsonError>status(HttpStatus.NOT_FOUND).body(new JsonError(e.getMessage()));
    }
}
