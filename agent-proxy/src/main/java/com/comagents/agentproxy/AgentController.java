package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_status.AgentExecutorInfoService;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class AgentController {

    private final AgentExecutorInfoService agentExecutorInfoService;

    public AgentController(AgentExecutorInfoService agentExecutorInfoService) {
        this.agentExecutorInfoService = agentExecutorInfoService;
    }

    /** This agent executor, whatever agent it runs. */
    @Get("/info")
    public AgentInfoResponse info() {
        return agentExecutorInfoService.getInfo();
    }
}
