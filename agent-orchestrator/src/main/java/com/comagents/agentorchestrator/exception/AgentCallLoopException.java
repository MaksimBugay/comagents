package com.comagents.agentorchestrator.exception;

import com.comagents.commons.domain.AiAgent;

import java.util.List;

/**
 * A start request for an agent that is already in its call chain ({@code agentCallSequence}): agent calls must not
 * loop. Answered with 422: the request is well-formed, but must not be carried out.
 */
public class AgentCallLoopException extends RuntimeException {

    public AgentCallLoopException(AiAgent agent, List<String> agentCallSequence) {
        super("Starting " + agent.getAgentName() + " would make a loop: it is already in the call chain "
                + String.join(" -> ", agentCallSequence));
    }
}
