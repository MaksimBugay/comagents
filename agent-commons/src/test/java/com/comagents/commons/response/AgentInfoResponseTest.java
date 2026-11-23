package com.comagents.commons.response;

import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInfoResponseTest {

    @Test
    void readsBackFromJson() {
        AgentInfoResponse info = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, "sequence-1");

        // every component, not just the device id equals() compares
        assertEquals(info.toString(), JsonUtility.fromJson(JsonUtility.toJson(info), AgentInfoResponse.class).toString());
    }

    @Test
    void createdNowByDefault() {
        long before = System.currentTimeMillis();

        AgentInfoResponse info = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, "sequence-1");
        // as read from JSON without createdAt
        AgentInfoResponse withoutCreatedAt = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1,
                "sequence-1", 0);

        assertTrue(info.createdAt() >= before && info.createdAt() <= System.currentTimeMillis());
        assertTrue(withoutCreatedAt.createdAt() >= before);
        assertEquals(1_700_000_000_000L, new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2,
                1, "sequence-1", 1_700_000_000_000L).createdAt());
    }

    @Test
    void equalByDeviceIdOnly() {
        AgentInfoResponse info = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, "sequence-1");
        AgentInfoResponse later = new AgentInfoResponse(AiHarnessProvider.CODEX, "0.2", "executor-device-1", 1, 0, 0, "sequence-2");

        assertEquals(info, later);
        assertEquals(info.hashCode(), later.hashCode());
        assertNotEquals(info, new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-2", 3, 2, 1, "sequence-1"));
    }

    @Test
    void infoWithoutSequenceIdIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, null));

        assertEquals("sequenceId must not be blank", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, " "));
    }
}
