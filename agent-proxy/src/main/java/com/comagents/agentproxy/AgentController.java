package com.comagents.agentproxy;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

@Controller("/{agentName}")
public class AgentController {

    private final String applicationName;
    private final String version;
    private final String instance;

    public AgentController(@Value("${micronaut.application.name}") String applicationName,
                           @Value("${agent-proxy.version:unknown}") String version,
                           @Value("${HOSTNAME:local}") String instance) {
        this.applicationName = applicationName;
        this.version = version;
        this.instance = instance;
    }

    @Get("/info")
    public AgentInfo info(@PathVariable String agentName) {
        return new AgentInfo(agentName, applicationName, version, instance);
    }
}
