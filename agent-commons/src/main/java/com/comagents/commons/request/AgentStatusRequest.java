package com.comagents.commons.request;

import com.comagents.commons.communication.WithOrchestratorDeviceId;
import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AiAgent;
import org.apache.commons.lang3.Validate;

import java.util.UUID;

/**
 * Asks an agent executor where a session stands.
 *
 * @param sequenceId           the sender's id of the request, which the response to it carries
 * @param orchestratorDeviceId the orchestrator device that gets the response
 */
public record AgentStatusRequest(AiAgent agent, UUID sessionId, String sequenceId, String orchestratorDeviceId)
        implements WithSequenceId, WithOrchestratorDeviceId {

    public AgentStatusRequest {
        Validate.notNull(agent, "agent must not be null");
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
        Validate.notBlank(orchestratorDeviceId, "orchestratorDeviceId must not be blank");
    }
}
