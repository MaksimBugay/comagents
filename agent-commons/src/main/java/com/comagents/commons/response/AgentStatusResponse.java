package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.request.AgentStatusRequest;
import org.apache.commons.lang3.Validate;

/** The answer to an {@link AgentStatusRequest}, with the request's sequence id. */
public record AgentStatusResponse(AgentStatusRequest request, AgentStatus status) implements WithSequenceId {

    public AgentStatusResponse {
        Validate.notNull(request, "request must not be null");
        Validate.notNull(status, "status must not be null");
    }

    @Override
    public String sequenceId() {
        return request().sequenceId();
    }
}
