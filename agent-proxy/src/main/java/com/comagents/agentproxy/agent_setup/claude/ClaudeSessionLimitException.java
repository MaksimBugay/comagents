package com.comagents.agentproxy.agent_setup.claude;

/** Thrown when starting a session would exceed the configured number of concurrent Claude Code runs. */
public class ClaudeSessionLimitException extends RuntimeException {

    public ClaudeSessionLimitException(int maxConcurrentSessions) {
        super("All " + maxConcurrentSessions + " Claude Code session slots are busy, try again later");
    }
}
