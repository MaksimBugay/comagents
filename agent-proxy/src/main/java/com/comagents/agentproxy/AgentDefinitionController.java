package com.comagents.agentproxy;

import com.comagents.commons.agent_definition.AgentDefinitionService;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.response.AgentConfigResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

@Controller("/agent-definition")
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;

    public AgentDefinitionController(AgentDefinitionService agentDefinitionService) {
        this.agentDefinitionService = agentDefinitionService;
    }

    /** The {@code agent.json} of each agent, in the order asked. */
    // downloads from the agent definitions' source (blocking I/O), which must not run on the Netty event loop
    @Get("/config")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentConfigResponse agentConfig(@QueryValue List<AiAgent> agents) {
        return AgentConfigResponse.of(agentDefinitionService.getAgentConfig(agents));
    }
}
