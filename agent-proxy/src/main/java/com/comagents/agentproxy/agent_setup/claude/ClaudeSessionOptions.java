package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.core.annotation.Nullable;

/**
 * What a session is started with besides its agent and working directory; kept in its archive, so a resumed run
 * (also after a restart) has the same.
 *
 * @param verboseOutput  whether thinking and thinking-token progress are shown (see {@link ClaudeAgentSession#isVerboseOutput})
 * @param actorAccountId the Pushca account that started the session, if any: the only member of its chat
 */
public record ClaudeSessionOptions(boolean verboseOutput, @Nullable String actorAccountId) {

    public static final ClaudeSessionOptions DEFAULT = new ClaudeSessionOptions(false, null);
}
