package com.comagents.commons.response;

import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AgentStatus;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.AgentStatusRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentStatusResponseTest {

    private final UUID sessionId = UUID.randomUUID();
    private final AgentStatusRequest request = new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1",
            "orchestrator-device-1");
    private final AgentStatus status = new AgentStatus(AiAgent.CODE_REVIEW, sessionId, AgentSessionState.RUNNING, 1L,
            null, false);

    @Test
    void sequenceIdIsTheRequests() {
        assertEquals("sequence-1", new AgentStatusResponse(request, status).sequenceId());
    }

    @Test
    void responseWithoutRequestOrStatusIsRejected() {
        assertEquals("request must not be null",
                assertThrows(NullPointerException.class, () -> new AgentStatusResponse(null, status)).getMessage());
        assertEquals("status must not be null",
                assertThrows(NullPointerException.class, () -> new AgentStatusResponse(request, null)).getMessage());
    }
}
