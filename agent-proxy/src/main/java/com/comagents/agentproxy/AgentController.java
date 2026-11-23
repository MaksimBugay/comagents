package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.AgentSessionStarter;
import com.comagents.agentproxy.agent_setup.NoAgentSetupServiceException;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.agentproxy.agent_status.AgentExecutorInfoService;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.InvalidAgentContextException;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
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

@Controller
public class AgentController {

    private final AgentExecutorInfoService agentExecutorInfoService;
    private final AgentSessionStarter agentSessionStarter;

    public AgentController(AgentExecutorInfoService agentExecutorInfoService,
                           AgentSessionStarter agentSessionStarter) {
        this.agentExecutorInfoService = agentExecutorInfoService;
        this.agentSessionStarter = agentSessionStarter;
    }

    /** This agent executor, whatever agent it runs. */
    @Get("/info")
    public AgentInfoResponse info() {
        return agentExecutorInfoService.getInfo();
    }

    /**
     * Prepares the session and starts the agent in the background: 202 Accepted. Follow it with
     * {@code GET /agent-status/{agentName}/{sessionId}}, then fetch {@code GET /agent-response/{agentName}/{sessionId}}.
     */
    // setup downloads and copies files (blocking I/O), which must not run on the Netty event loop
    @Post("/{agentName}/start")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<AgentInfoResponse> start(@PathVariable @NotBlank String agentName, @Body @Valid StartAgentSessionRequest startAgentRequest) {
        requireSameAgent(agentName, startAgentRequest.agent());
        return HttpResponse.accepted().body(agentSessionStarter.start(startAgentRequest));
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

    @Error(exception = NoAgentSetupServiceException.class)
    public HttpResponse<JsonError> noSetupService(HttpRequest<?> ignoredRequest, NoAgentSetupServiceException e) {
        return HttpResponse.<JsonError>status(HttpStatus.NOT_IMPLEMENTED).body(new JsonError(e.getMessage()));
    }

    @Error(exception = ClaudeSessionExistsException.class)
    public HttpResponse<JsonError> sessionExists(HttpRequest<?> ignoredRequest, ClaudeSessionExistsException e) {
        return HttpResponse.<JsonError>status(HttpStatus.CONFLICT).body(new JsonError(e.getMessage()));
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
