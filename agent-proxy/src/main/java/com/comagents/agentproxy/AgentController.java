package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.AgentSetupService;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionLimitException;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import com.comagents.agentproxy.request.StartAgentRequest;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller("/{agentName}")
public class AgentController {

    private final String applicationName;
    private final String version;
    private final String instance;
    private final Map<AiHarnessProvider, AgentSetupService> agentSetupServiceMap;

    public AgentController(@Value("${micronaut.application.name}") String applicationName,
                           @Value("${agent-proxy.version:unknown}") String version,
                           @Value("${HOSTNAME:local}") String instance,
                           List<AgentSetupService> agentSetupServiceList) {
        this.applicationName = applicationName;
        this.version = version;
        this.instance = instance;
        // toMap fails fast at startup if two setup services claim the same provider
        this.agentSetupServiceMap = agentSetupServiceList.stream()
                .collect(Collectors.toMap(AgentSetupService::getAiHarnessProvider, Function.identity()));
    }

    @Get("/info")
    public AgentInfo info(@PathVariable String agentName) {
        return new AgentInfo(agentName, applicationName, version, instance);
    }

    // setup downloads and copies files (blocking I/O), which must not run on the Netty event loop
    @Post("/start")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentInfo start(@PathVariable @NotBlank String agentName, @Body @Valid StartAgentRequest startAgentRequest) {
        requireSameAgent(agentName, startAgentRequest.agent());
        setupServiceFor(startAgentRequest.agent()).setup(
                startAgentRequest.agent(),
                new AiAgentAttributes(
                        startAgentRequest.sessionId(),
                        startAgentRequest.cloneSourceUrl()
                )
        );
        return new AgentInfo(startAgentRequest.agent().getAgentName(), applicationName, version, instance);
    }



    @Error(exception = ClaudeSessionLimitException.class)
    public HttpResponse<JsonError> sessionLimitReached(HttpRequest<?> request, ClaudeSessionLimitException e) {
        return HttpResponse.<JsonError>status(HttpStatus.TOO_MANY_REQUESTS).body(new JsonError(e.getMessage()));
    }

    private AgentSetupService setupServiceFor(AiAgent agent) {
        AgentSetupService agentSetupService = agentSetupServiceMap.get(agent.getAiHarnessProvider());
        if (agentSetupService == null) {
            // the agent is known but no setup service bean exists for its harness provider yet
            throw new HttpStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "No setup service for harness provider " + agent.getAiHarnessProvider() + " (agent " + agent.getAgentName() + ")");
        }
        return agentSetupService;
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
