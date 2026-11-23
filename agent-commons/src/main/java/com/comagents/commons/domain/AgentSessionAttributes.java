package com.comagents.commons.domain;

import com.comagents.commons.domain.agent_context.AgentContext;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * @param agentContext                what the session works with (see {@code StartAgentSessionRequest}); null for none
 * @param sequenceId                  the start request's sequence id, which the session's response carries
 * @param originatorDeviceId          the orchestrator device that sent the start request and gets the replies to it
 *                                    and the session's response
 * @param agentResponseWaiterDeviceId the start request's other device that gets the session's response too; none
 *                                    when null or blank
 * @param agentExecutorDeviceId       the agent executor device that runs the session
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
                                     @NotNull String agentExecutorDeviceId,
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
