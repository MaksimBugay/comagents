package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextItemTest {

    @Test
    void requiredItemNeedsValue() {
        for (String value : new String[]{null, "", "  "}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "sources", true), value));
            assertEquals("Required item sources has no value", e.getMessage());
        }
    }

    @Test
    void optionalItemMayHaveNoValue() {
        assertDoesNotThrow(() -> new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.STRING, "note", false), null));
        assertDoesNotThrow(() -> new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.STRING, "note", false), " "));
        assertDoesNotThrow(() -> new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.STRING, "note", true), "text"));
    }
}
