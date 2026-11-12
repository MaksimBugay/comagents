package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentResponse;
import com.comagents.agentproxy.response.AgentResponseUnavailable;
import com.comagents.agentproxy.response.AgentStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.UUID;

@Controller("/agent-response")
public class AgentResponseController {

    private final AgentResponseService agentResponseService;
    private final AgentStatusService agentStatusService;

    public AgentResponseController(AgentResponseService agentResponseService, AgentStatusService agentStatusService) {
        this.agentResponseService = agentResponseService;
        this.agentStatusService = agentStatusService;
    }

    /**
     * 200 with the agent's response, or 404 with {@link AgentResponseUnavailable}, whose {@code state} tells the
     * caller whether to try again ({@code RUNNING}) or not.
     */
    // reads the response file (blocking I/O), which must not run on the Netty event loop
    @Get("/{agentName}/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> agentResponse(@PathVariable String agentName, @PathVariable UUID sessionId) {
        AiAgent agent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        return agentResponseService.findResponse(agent, sessionId)
                .<HttpResponse<?>>map(HttpResponse::ok)
                .orElseGet(() -> HttpResponse.notFound(unavailable(agent, sessionId)));
    }

    private AgentResponseUnavailable unavailable(AiAgent agent, UUID sessionId) {
        AgentSessionState state = agentStatusService.findStatus(agent, sessionId).map(AgentStatus::state).orElse(null);
        String reason = state == null ? "session not found (never started, or already cleaned up)" : "session is " + state;
        return new AgentResponseUnavailable(
                "No response from agent " + agent.getAgentName() + " for session " + sessionId + ": " + reason, state);
    }
}
