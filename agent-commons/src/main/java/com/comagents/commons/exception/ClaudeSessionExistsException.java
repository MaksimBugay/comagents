package com.comagents.commons.exception;

import java.util.UUID;

/** Thrown when a new session would take a session id that is already in use: being set up, registered, or on disk. */
public class ClaudeSessionExistsException extends IllegalStateException {

    public ClaudeSessionExistsException(UUID sessionId, String reason) {
        super("Claude session " + sessionId + " " + reason + "; start it with a new session id");
    }

    /** With the message as received: how an orchestrator recreates it from an executor's error. */
    public ClaudeSessionExistsException(String message) {
        super(message);
    }
}
