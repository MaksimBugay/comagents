package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.controller.request.StartAgentSessionExternalRequest;
import com.comagents.agentorchestrator.controller.response.StartAgentSessionExternalResponse;
import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.agentorchestrator.exception.TooManyStartSessionRequestsException;
import com.comagents.commons.domain.agent_context.InvalidAgentContextException;
import com.comagents.commons.exception.ClaudeSessionExistsException;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.commons.exception.NoAgentSetupServiceException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import tools.jackson.core.JacksonException;

import java.util.Optional;
import java.util.UUID;

@Controller
public class AgentController {

    private final StartAgentSessionHelper startAgentSessionHelper;

    public AgentController(StartAgentSessionHelper startAgentSessionHelper) {
        this.startAgentSessionHelper = startAgentSessionHelper;
    }

    /**
     * Starts a session of the request's agent, with a new session id, on the best executor for it: 202 Accepted.
     * Fetch its response with {@code GET /agent-response/{sessionId}}.
     */
    // waits for the executor's reply, which must not block the Netty event loop
    @Post("/start")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<StartAgentSessionExternalResponse> start(
            @Body @Valid StartAgentSessionExternalRequest startAgentRequest) {
        UUID sessionId = UUID.randomUUID();
        startAgentSessionHelper.startAgentSession(
                sessionId,
                startAgentRequest.agent(),
                startAgentRequest.agentContext(),
                startAgentRequest.agentCallSequence(),
                startAgentRequest.actorAccountId(),
                startAgentRequest.agentResponseWaiterDeviceId(),
                startAgentRequest.loggingLevel()
        );
        return HttpResponse.accepted().body(new StartAgentSessionExternalResponse(sessionId));
    }

    @Error(exception = ClaudeSessionLimitException.class)
    public HttpResponse<JsonError> sessionLimitReached(HttpRequest<?> ignoredRequest, ClaudeSessionLimitException e) {
        return HttpResponse.<JsonError>status(HttpStatus.TOO_MANY_REQUESTS).body(new JsonError(e.getMessage()));
    }

    @Error(exception = TooManyStartSessionRequestsException.class)
    public HttpResponse<JsonError> tooManyStartSessionRequests(HttpRequest<?> ignoredRequest,
                                                               TooManyStartSessionRequestsException e) {
        return HttpResponse.<JsonError>status(HttpStatus.TOO_MANY_REQUESTS).body(new JsonError(e.getMessage()));
    }

    @Error(exception = ComagentsResponseTimeoutException.class)
    public HttpResponse<JsonError> executorTimedOut(HttpRequest<?> ignoredRequest,
                                                    ComagentsResponseTimeoutException e) {
        return HttpResponse.<JsonError>status(HttpStatus.GATEWAY_TIMEOUT).body(new JsonError(e.getMessage()));
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
}
