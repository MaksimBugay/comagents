package com.comagents.commons.exception;

import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentResponseNotReadyExceptionTest {

    @Test
    void theStateSurvivesTheMessage() {
        AgentResponseNotReadyException sent = new AgentResponseNotReadyException(AiAgent.CODE_REVIEW,
                UUID.randomUUID(), AgentSessionState.WAITING);

        AgentResponseNotReadyException received = new AgentResponseNotReadyException(sent.getMessage());

        assertEquals(sent.getMessage(), received.getMessage());
        assertEquals(AgentSessionState.WAITING, received.getState());
    }

    @Test
    void noStateFromAMessageWithoutOne() {
        assertNull(new AgentResponseNotReadyException("not ready").getState());
        assertNull(new AgentResponseNotReadyException("session is SLEEPING").getState());
        assertNull(new AgentResponseNotReadyException((String) null).getState());
    }
}
