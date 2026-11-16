package com.comagents.agentproxy.agent_setup.claude;

import java.util.UUID;

/**
 * Follows Claude Code sessions from outside the registry, e.g. to mirror their output into a chat. Called on the
 * session's output thread: implementations must return quickly and never throw.
 */
public interface ClaudeSessionListener {

    /** One summarised line of the session's output, as logged; verbose output only for verbose sessions. */
    void onOutput(UUID sessionId, String output);

    /**
     * The agent's final answer, reported after all of its output when the run ends with a valid one: the text, or,
     * for an agent with a response schema, the response context as JSON (the session's response file holds it),
     * unless the run ended with an error and no structured output: then its error text.
     */
    void onResponse(UUID sessionId, String response);

    /** Everything the session left behind was deleted; nothing more will be reported for it. */
    void onSessionDataDeleted(UUID sessionId);
}
