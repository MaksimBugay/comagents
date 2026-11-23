package com.comagents.commons.request;

import com.comagents.commons.communication.WithOriginatorDeviceId;
import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AiAgent;
import org.apache.commons.lang3.Validate;

import java.util.UUID;

/**
 * Asks an agent executor for a session's response.
 *
 * @param sequenceId         the sender's id of the request, which the response to it carries
 * @param originatorDeviceId the orchestrator device that gets the response
 */
public record AgentResponseRequest(AiAgent agent, UUID sessionId, String sequenceId, String originatorDeviceId)
        implements WithSequenceId, WithOriginatorDeviceId {

    public AgentResponseRequest {
        Validate.notNull(agent, "agent must not be null");
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
        Validate.notBlank(originatorDeviceId, "originatorDeviceId must not be blank");
    }
}
