package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentStatus;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.exceptions.HttpStatusException;
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
        return agentStatusService.findStatus(agent, sessionId)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                        "No session " + sessionId + " for agent " + agentName));
    }
}
