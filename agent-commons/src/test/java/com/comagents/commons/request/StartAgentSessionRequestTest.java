package com.comagents.commons.request;

import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartAgentSessionRequestTest {

    @Test
    void readsBackFromJson() {
        StartAgentSessionRequest request = new StartAgentSessionRequest(UUID.randomUUID(), AiAgent.CODE_REVIEW, null,
                "sequence-1", "orchestrator-device-1", List.of("orchestrator", "CODE_REVIEW"), "MBugai",
                AgentSessionLoggingLevel.INFO, 1_700_000_000_000L);

        assertEquals(request, JsonUtility.fromJson(JsonUtility.toJson(request), StartAgentSessionRequest.class));
    }

    @Test
    void agentCallSequenceIsEmptyByDefault() {
        StartAgentSessionRequest request = JsonUtility.fromJson("""
                {"agent": "CODE_REVIEW", "sequenceId": "sequence-1", "originatorDeviceId": "orchestrator-device-1"}""",
                StartAgentSessionRequest.class);

        assertEquals(List.of(), request.agentCallSequence());
        assertEquals(List.of(), new StartAgentSessionRequest(UUID.randomUUID(), AiAgent.CODE_REVIEW, null,
                "sequence-1", "orchestrator-device-1", "MBugai").agentCallSequence());
    }
}
