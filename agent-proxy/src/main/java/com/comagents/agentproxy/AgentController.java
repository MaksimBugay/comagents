package com.comagents.agentproxy;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.request.StartAgentRequest;
import com.comagents.agentproxy.vcs.VcsService;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Controller("/{agentName}")
public class AgentController {

    private final String applicationName;
    private final String version;
    private final String instance;
    private final VcsService vcsService;

    public AgentController(@Value("${micronaut.application.name}") String applicationName,
                           @Value("${agent-proxy.version:unknown}") String version,
                           @Value("${HOSTNAME:local}") String instance,
                           VcsService vcsService) {
        this.applicationName = applicationName;
        this.version = version;
        this.instance = instance;
        this.vcsService = vcsService;
    }

    @Get("/info")
    public AgentInfo info(@PathVariable String agentName) {
        return new AgentInfo(agentName, applicationName, version, instance);
    }

    @Post("/info")
    public AgentInfo start(@PathVariable @NotBlank String agentName, @Body @Valid StartAgentRequest startAgentRequest) {
        requireSameAgent(agentName, startAgentRequest.agent());
        if (StringUtils.isNotEmpty(startAgentRequest.cloneSourceUrl())) {
            vcsService.clone(startAgentRequest.cloneSourceUrl(), startAgentRequest.sessionId());
        }
        vcsService.cloneComAgentsSources();
        return new AgentInfo(startAgentRequest.agent().getAgentName(), applicationName, version, instance);
    }

    private static void requireSameAgent(String agentName, AiAgent requestAgent) {
        AiAgent pathAgent = AiAgent.fromAgentName(agentName)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unknown agent: " + agentName));
        if (pathAgent != requestAgent) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST,
                    "Agent in path (" + agentName + ") does not match agent in request (" + requestAgent.getAgentName() + ")");
        }
    }
}
