package com.comagents.commons.request;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionCleanupRequestTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    void readsBackFromJson() {
        SessionCleanupRequest request = new SessionCleanupRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-1");

        assertEquals(request, JsonUtility.fromJson(JsonUtility.toJson(request), SessionCleanupRequest.class));
    }

    @Test
    void incompleteRequestsAreRejected() {
        assertThrows(NullPointerException.class, () -> new SessionCleanupRequest(null, sessionId, "sequence-1"));
        assertThrows(NullPointerException.class, () -> new SessionCleanupRequest(AiAgent.CODE_REVIEW, null, "sequence-1"));
        assertThrows(IllegalArgumentException.class, () -> new SessionCleanupRequest(AiAgent.CODE_REVIEW, sessionId, " "));
    }
}
