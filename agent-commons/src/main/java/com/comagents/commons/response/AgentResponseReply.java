package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.request.AgentResponseRequest;
import org.apache.commons.lang3.Validate;

/**
 * The answer to an {@link AgentResponseRequest}. It has no sequence id of its own: the message carries the
 * {@link AgentResponseRequest}'s, while {@code response} keeps the start request's.
 */
public record AgentResponseReply(AgentSessionResponse response) implements WithSequenceId {

    public AgentResponseReply {
        Validate.notNull(response, "response must not be null");
    }

    @Override
    public String sequenceId() {
        return response().sequenceId();
    }
}
