package com.comagents.commons.response;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSessionResponseTest {

    @Test
    void sequenceIdIsTheRequests() {
        StartAgentSessionRequest request = new StartAgentSessionRequest(UUID.randomUUID(), AiAgent.CODE_REVIEW, null,
                "sequence-1", "orchestrator-device-1", "MBugai");

        assertEquals("sequence-1", new AgentSessionResponse(request, null, 0, "executor-device-1").sequenceId());
    }

    @Test
    void responseWithoutRequestIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new AgentSessionResponse(null, null, 0, "executor-device-1"));

        assertEquals("request must not be null", e.getMessage());
    }
}
