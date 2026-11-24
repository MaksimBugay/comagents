package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.commons.exception.AgentResponseNotReadyException;
import com.comagents.commons.exception.AgentSessionNotFoundException;
import com.comagents.commons.response.AgentResponseUnavailable;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.UUID;

@Controller("/agent-response")
public class AgentResponseController {

    private final AgentSessionResponseHelper agentSessionResponseHelper;

    public AgentResponseController(AgentSessionResponseHelper agentSessionResponseHelper) {
        this.agentSessionResponseHelper = agentSessionResponseHelper;
    }

    /**
     * 200 with the agent's response, or 404 with {@link AgentResponseUnavailable}: with the session's {@code state}
     * when the response isn't ready yet (try again), without one when the session is unknown; 504 when the session's
     * executor didn't reply in time.
     */
    // may wait for the executor's reply, which must not block the Netty event loop
    @Get("/{sessionId}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public AgentSessionResponse agentResponse(@PathVariable UUID sessionId) {
        return agentSessionResponseHelper.getResponse(sessionId);
    }

    @Error(exception = AgentResponseNotReadyException.class)
    public HttpResponse<AgentResponseUnavailable> responseNotReady(HttpRequest<?> ignoredRequest,
                                                                   AgentResponseNotReadyException e) {
        return HttpResponse.notFound(new AgentResponseUnavailable(e.getMessage(), e.getState()));
    }

    @Error(exception = ComagentsResponseTimeoutException.class)
    public HttpResponse<JsonError> executorTimedOut(HttpRequest<?> ignoredRequest,
                                                    ComagentsResponseTimeoutException e) {
        return HttpResponse.<JsonError>status(HttpStatus.GATEWAY_TIMEOUT).body(new JsonError(e.getMessage()));
    }

    @Error(exception = AgentSessionNotFoundException.class)
    public HttpResponse<AgentResponseUnavailable> sessionNotFound(HttpRequest<?> ignoredRequest,
                                                                  AgentSessionNotFoundException e) {
        return HttpResponse.notFound(new AgentResponseUnavailable(e.getMessage(), null));
    }
}
