package com.comagents.commons.request;

import com.comagents.commons.communication.WithOriginatorDeviceId;
import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param agentContext                what the session works with: its primary repository is cloned as the working
 *                                    directory, and all its items are added to the kick-off prompt; without one nothing
 *                                    is cloned
 * @param sequenceId                  the sender's id of the request, which the responses to it carry
 * @param originatorDeviceId          the orchestrator device that sent the request and gets the replies to it and the
 *                                    session's response
 * @param agentResponseWaiterDeviceId another device that gets the session's response too; none when null or blank
 * @param agentCallSequence           the agent call sequence the session belongs to; empty when not given
 */
public record StartAgentSessionRequest(UUID sessionId,
                                       @NotNull AiAgent agent,
                                       @Nullable AgentContext agentContext,
                                       @NotBlank String sequenceId,
                                       @NotBlank String originatorDeviceId,
                                       @Nullable String agentResponseWaiterDeviceId,
                                       List<String> agentCallSequence,
                                       String actorAccountId,
                                       AgentSessionLoggingLevel loggingLevel,
                                       long createdAt) implements WithSequenceId, WithOriginatorDeviceId {

    public StartAgentSessionRequest {
        agentCallSequence = agentCallSequence == null ? List.of() : List.copyOf(agentCallSequence);
    }

    public StartAgentSessionRequest(UUID sessionId, AiAgent agent, @Nullable AgentContext agentContext, String sequenceId, String originatorDeviceId, String actorAccountId) {
        this(sessionId, agent, agentContext, sequenceId, originatorDeviceId, null, List.of(), actorAccountId, AgentSessionLoggingLevel.INFO, Instant.now().toEpochMilli());
    }
}
