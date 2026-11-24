package com.comagents.agentorchestrator.exception;

/**
 * No executor of the agent's harness with a free slot is in the discovery registry now. Answered with 429: try again
 * later.
 */
public class TooManyStartSessionRequestsException extends RuntimeException {

    public TooManyStartSessionRequestsException(String message) {
        super(message);
    }
}
