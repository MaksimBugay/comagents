package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.commons.domain.AgentSessionAttributes;
import io.micronaut.core.annotation.Nullable;

/**
 * What a session is started with besides its agent and working directory; kept in its archive, so a resumed run
 * (also after a restart) has the same.
 *
 * @param verboseOutput  whether thinking and thinking-token progress are shown (see {@link ClaudeAgentSession#isVerboseOutput})
 * @param actorAccountId the Pushca account that started the session, if any: the only member of its chat
 * @param attributes     what the session was started with, to answer with its response; null when not started by a
 *                       request
 */
public record ClaudeSessionOptions(boolean verboseOutput, @Nullable String actorAccountId,
                                   @Nullable AgentSessionAttributes attributes) {

    public static final ClaudeSessionOptions DEFAULT = new ClaudeSessionOptions(false, null, null);

    public ClaudeSessionOptions(boolean verboseOutput, @Nullable String actorAccountId) {
        this(verboseOutput, actorAccountId, null);
    }
}
