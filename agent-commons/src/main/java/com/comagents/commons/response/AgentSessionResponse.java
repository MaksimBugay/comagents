package com.comagents.commons.response;

import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.request.StartAgentSessionRequest;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.Validate;

public record AgentSessionResponse(@NotNull StartAgentSessionRequest request, AgentContext agentContext, long createdAt,
                                   String executorDeviceId) implements WithSequenceId {

    public AgentSessionResponse {
        Validate.notNull(request, "request must not be null");
    }

    @Override
    public String sequenceId() {
        return request().sequenceId();
    }
}
