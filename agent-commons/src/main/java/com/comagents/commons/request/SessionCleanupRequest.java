package com.comagents.commons.request;

import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AiAgent;
import org.apache.commons.lang3.Validate;

import java.util.UUID;

/**
 * Tells the agent executor that ran a session to delete the session's files now: the orchestrator has its response.
 * Nothing answers it; an executor that never gets it deletes them after {@code agent-response.cleanup-delay}.
 *
 * @param sequenceId the sender's id of the request
 */
public record SessionCleanupRequest(AiAgent agent, UUID sessionId, String sequenceId) implements WithSequenceId {

    public SessionCleanupRequest {
        Validate.notNull(agent, "agent must not be null");
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
    }
}
