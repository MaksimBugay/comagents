package com.comagents.commons.domain;

import com.comagents.commons.domain.agent_context.AgentContext;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * @param agentContext                what the session works with (see {@code StartAgentSessionRequest}); null for none
 * @param sequenceId                  the start request's sequence id, which the session's response carries
 * @param originatorDeviceId          the orchestrator device that sent the start request and gets the replies to it;
 *                                    it also gets the session's response when there is no agentResponseWaiterDeviceId
 * @param agentResponseWaiterDeviceId the start request's device that waits for the session's response; the
 *                                    originator's when null or blank
 * @param agentCallSequence           the start request's agent call sequence; empty when not given
 * @param actorAccountId              the Pushca account that started the session: the only member of the session's chat
 * @param loggingLevel                what the session's chat shows; {@link AgentSessionLoggingLevel#INFO} when not given
 * @param createdAt                   when the start request was created, in epoch milliseconds
 */
public record AgentSessionAttributes(@NotNull UUID sessionId,
                                     @Nullable AgentContext agentContext,
                                     @NotNull String sequenceId,
                                     @NotNull String originatorDeviceId,
                                     @Nullable String agentResponseWaiterDeviceId,
                                     List<String> agentCallSequence,
                                     @Nullable String actorAccountId,
                                     AgentSessionLoggingLevel loggingLevel,
                                     long createdAt) {

    public AgentSessionAttributes {
        agentCallSequence = agentCallSequence == null ? List.of() : List.copyOf(agentCallSequence);
        if (loggingLevel == null) {
            loggingLevel = AgentSessionLoggingLevel.INFO;
        }
    }
}
