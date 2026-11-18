package com.comagents.commons.response;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentResponseReplyTest {

    @Test
    void readsBackFromJson() {
        AgentResponseReply reply = new AgentResponseReply(new AgentSessionResponse(new StartAgentSessionRequest(
                UUID.randomUUID(), AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"),
                new AgentContext(List.of(new StringAgentContextItem("response", "done"))), 1L, "executor-device-1"));

        assertEquals(reply, JsonUtility.fromJson(JsonUtility.toJson(reply), AgentResponseReply.class));
    }

    @Test
    void replyWithoutResponseIsRejected() {
        assertEquals("response must not be null",
                assertThrows(NullPointerException.class, () -> new AgentResponseReply(null)).getMessage());
    }
}
