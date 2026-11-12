package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentResponse;
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

    public AgentResponseController(AgentResponseService agentResponseService) {
        this.agentResponseService = agentResponseService;
    }

    // reads the response file (blocking I/O), which must not run on the Netty event loop
    @Get("/{agentName}/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentResponse agentResponse(@PathVariable String agentName, @PathVariable UUID sessionId) {
        AiAgent agent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        return agentResponseService.findResponse(agent, sessionId)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                        "No response from agent " + agentName + " for session " + sessionId));
    }
}
