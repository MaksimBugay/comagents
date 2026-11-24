package com.comagents.agentorchestrator.exception;

import java.util.concurrent.TimeoutException;

/**
 * No response to a request to an agent executor came within {@code agent-orchestrator.response-timeout}. Answered
 * with 504.
 */
public class ComagentsResponseTimeoutException extends RuntimeException {

    public ComagentsResponseTimeoutException(String message, TimeoutException cause) {
        super(message, cause);
    }
}
