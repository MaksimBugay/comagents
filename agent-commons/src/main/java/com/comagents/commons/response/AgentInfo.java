package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import org.apache.commons.lang3.Validate;

/**
 * An agent-proxy (agent executor) and the agent it runs.
 *
 * @param deviceId   the executor's device id
 * @param sequenceId the start request's sequence id; a new random UUID when this answers no request ({@code GET /info})
 */
public record AgentInfo(String name, String version, String deviceId, String sequenceId) implements WithSequenceId {

    public AgentInfo {
        // the id of the ComagentsMessage that carries it
        Validate.notBlank(sequenceId, "sequenceId must not be blank");
    }
}
