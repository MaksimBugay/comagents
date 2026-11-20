package com.comagents.commons.response;

import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentInfoTest {

    @Test
    void readsBackFromJson() {
        AgentInfo info = new AgentInfo(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, "sequence-1");

        assertEquals(info, JsonUtility.fromJson(JsonUtility.toJson(info), AgentInfo.class));
    }

    @Test
    void infoWithoutSequenceIdIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new AgentInfo(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, null));

        assertEquals("sequenceId must not be blank", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new AgentInfo(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 1, " "));
    }
}
