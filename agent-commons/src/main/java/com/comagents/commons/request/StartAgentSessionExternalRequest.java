package com.comagents.commons.request;

import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The body of the orchestrator's {@code POST /start}: a session of {@code agent} to start on the best executor for it.
 *
 * @param agentCallSequence           the agents that called this one, the caller last; empty when a person starts
 *                                    it. The orchestrator appends the started agent, and refuses one already in it:
 *                                    agent calls must not loop
 * @param agentResponseWaiterDeviceId another device that gets the session's response too; none when null or blank
 */
public record StartAgentSessionExternalRequest(@NotNull AiAgent agent,
                                               @Nullable AgentContext agentContext,
                                               List<String> agentCallSequence,
                                               String actorAccountId,
                                               @Nullable String agentResponseWaiterDeviceId,
                                               AgentSessionLoggingLevel loggingLevel) {
    @SuppressWarnings("unused")
    public StartAgentSessionExternalRequest(AiAgent agent, @Nullable AgentContext agentContext, String actorAccountId) {
        this(agent, agentContext, List.of(), actorAccountId, null, AgentSessionLoggingLevel.INFO);
    }
}
