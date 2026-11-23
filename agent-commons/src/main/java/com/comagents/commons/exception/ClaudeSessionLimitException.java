package com.comagents.commons.exception;

/**
 * Thrown when starting a session would exceed the configured number of concurrent Claude Code runs. Shared, so an
 * orchestrator recognizes it in an executor's reply and tries another executor.
 */
public class ClaudeSessionLimitException extends RuntimeException {

    public ClaudeSessionLimitException(int maxConcurrentSessions) {
        this("All " + maxConcurrentSessions + " Claude Code session slots are busy, try again later");
    }

    /** With the message as received: how an orchestrator recreates it from an executor's error. */
    public ClaudeSessionLimitException(String message) {
        super(message);
    }
}
