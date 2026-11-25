package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.commons.domain.AiAgent;
import io.micronaut.core.annotation.Introspected;

import java.util.UUID;

/**
 * A call of another agent that a run ended its turn on, unanswered: the session is resumed with the called session's
 * response as soon as it arrives. Kept in the session's archive, so also after a restart.
 *
 * @param agent           the called agent
 * @param calledSessionId the called agent's session, whose response resumes the caller
 */
@Introspected
public record DeferredAgentCall(AiAgent agent, UUID calledSessionId) {
}
