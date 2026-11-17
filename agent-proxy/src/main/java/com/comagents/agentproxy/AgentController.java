package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.AgentSetupService;
import com.comagents.agentproxy.agent_setup.AgentSetupServices;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionLimitException;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.agentproxy.domain.agent_context.InvalidAgentContextException;
import com.comagents.agentproxy.request.StartAgentSessionRequest;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
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
import tools.jackson.core.JacksonException;

import java.util.Optional;

@Controller("/{agentName}")
public class AgentController {

    private final String applicationName;
    private final String version;
    private final String instance;
    private final AgentSetupServices agentSetupServices;

    public AgentController(@Value("${micronaut.application.name}") String applicationName,
                           @Value("${agent-proxy.version:unknown}") String version,
                           @Value("${HOSTNAME:local}") String instance,
                           AgentSetupServices agentSetupServices) {
        this.applicationName = applicationName;
        this.version = version;
        this.instance = instance;
        this.agentSetupServices = agentSetupServices;
    }

    @Get("/info")
    public AgentInfo info(@PathVariable String agentName) {
        return new AgentInfo(agentName, applicationName, version, instance);
    }

    /**
     * Prepares the session and starts the agent in the background: 202 Accepted. Follow it with
     * {@code GET /agent-status/{agentName}/{sessionId}}, then fetch {@code GET /agent-response/{agentName}/{sessionId}}.
     */
    // setup downloads and copies files (blocking I/O), which must not run on the Netty event loop
    @Post("/start")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<AgentInfo> start(@PathVariable @NotBlank String agentName, @Body @Valid StartAgentSessionRequest startAgentRequest) {
        requireSameAgent(agentName, startAgentRequest.agent());
        setupServiceFor(startAgentRequest.agent()).setup(
                startAgentRequest.agent(),
                new AgentSessionAttributes(
                        startAgentRequest.sessionId(),
                        startAgentRequest.agentContext(),
                        startAgentRequest.actorAccountId(),
                        startAgentRequest.loggingLevel(),
                        startAgentRequest.createdAt()
                )
        );
        return HttpResponse.accepted().body(new AgentInfo(startAgentRequest.agent().getAgentName(), applicationName, version, instance));
    }

    @Error(exception = ClaudeSessionLimitException.class)
    public HttpResponse<JsonError> sessionLimitReached(HttpRequest<?> ignoredRequest, ClaudeSessionLimitException e) {
        return HttpResponse.<JsonError>status(HttpStatus.TOO_MANY_REQUESTS).body(new JsonError(e.getMessage()));
    }

    @Error(exception = InvalidAgentContextException.class)
    public HttpResponse<JsonError> invalidAgentContext(HttpRequest<?> ignoredRequest, InvalidAgentContextException e) {
        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST).body(new JsonError(e.getMessage()));
    }

    // an AgentContext rejects itself while the request body is read; Jackson wraps that rejection, so its message
    // would otherwise be lost behind "Invalid JSON" (or "Failed to convert argument")
    @Error(exception = JacksonException.class)
    public HttpResponse<JsonError> unparsableRequest(HttpRequest<?> ignoredRequest, JacksonException e) {
        String message = invalidAgentContextCause(e).map(Throwable::getMessage).orElse("Invalid JSON: " + e.getOriginalMessage());
        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST).body(new JsonError(message));
    }

    @Error(exception = ConversionErrorException.class)
    public HttpResponse<JsonError> unreadableRequest(HttpRequest<?> ignoredRequest, ConversionErrorException e) {
        String message = invalidAgentContextCause(e).map(Throwable::getMessage).orElse(e.getMessage());
        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST).body(new JsonError(message));
    }

    private static Optional<InvalidAgentContextException> invalidAgentContextCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidAgentContextException invalid) {
                return Optional.of(invalid);
            }
        }
        return Optional.empty();
    }

    @Error(exception = ClaudeSessionExistsException.class)
    public HttpResponse<JsonError> sessionExists(HttpRequest<?> ignoredRequest, ClaudeSessionExistsException e) {
        return HttpResponse.<JsonError>status(HttpStatus.CONFLICT).body(new JsonError(e.getMessage()));
    }

    private AgentSetupService setupServiceFor(AiAgent agent) {
        // the agent is known but no setup service bean exists for its harness provider yet
        return agentSetupServices.forAgent(agent).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_IMPLEMENTED,
                "No setup service for harness provider " + agent.getAiHarnessProvider() + " (agent " + agent.getAgentName() + ")"));
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
