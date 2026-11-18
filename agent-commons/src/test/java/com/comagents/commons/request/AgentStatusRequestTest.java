package com.comagents.commons.request;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentStatusRequestTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    void readsBackFromJson() {
        AgentStatusRequest request = new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1",
                "orchestrator-device-1");

        assertEquals(request, JsonUtility.fromJson(JsonUtility.toJson(request), AgentStatusRequest.class));
    }

    @Test
    void incompleteRequestsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new AgentStatusRequest(null, sessionId, "sequence-1", "orchestrator-device-1"));
        assertThrows(NullPointerException.class,
                () -> new AgentStatusRequest(AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, " ", "orchestrator-device-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentStatusRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1", " "));
    }
}
