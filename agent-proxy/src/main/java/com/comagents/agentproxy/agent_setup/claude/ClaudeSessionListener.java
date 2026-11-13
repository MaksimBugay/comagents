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
     * The agent's final answer (the session's response file holds the same text), reported after all of its
     * output when the run ends with one.
     */
    void onResponse(UUID sessionId, String response);

    /** Everything the session left behind was deleted; nothing more will be reported for it. */
    void onSessionDataDeleted(UUID sessionId);
}
