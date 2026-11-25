package com.comagents.agentproxy.agent_call;

import com.comagents.commons.request.StartAgentSessionExternalRequest;
import com.comagents.commons.response.StartAgentSessionExternalResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/**
 * The agent orchestrator's HTTP API ({@code micronaut.http.services.agent-orchestrator}), as the agent tools use it.
 */
@Client(id = "agent-orchestrator")
public interface AgentOrchestratorClient {

    /**
     * Starts a session of the request's agent on the best executor for it.
     *
     * @throws io.micronaut.http.client.exceptions.HttpClientResponseException when the orchestrator refuses it
     * @throws io.micronaut.http.client.exceptions.HttpClientException         when the orchestrator can't be reached
     */
    @Post("/start")
    StartAgentSessionExternalResponse start(@Body StartAgentSessionExternalRequest request);
}
