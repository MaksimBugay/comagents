package com.comagents.commons.request;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentResponseRequestTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    void readsBackFromJson() {
        AgentResponseRequest request = new AgentResponseRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-2",
                "orchestrator-device-1");

        assertEquals(request, JsonUtility.fromJson(JsonUtility.toJson(request), AgentResponseRequest.class));
    }

    @Test
    void incompleteRequestsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new AgentResponseRequest(null, sessionId, "sequence-2", "orchestrator-device-1"));
        assertThrows(NullPointerException.class,
                () -> new AgentResponseRequest(AiAgent.CODE_REVIEW, null, "sequence-2", "orchestrator-device-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResponseRequest(AiAgent.CODE_REVIEW, sessionId, " ", "orchestrator-device-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResponseRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-2", " "));
    }
}
