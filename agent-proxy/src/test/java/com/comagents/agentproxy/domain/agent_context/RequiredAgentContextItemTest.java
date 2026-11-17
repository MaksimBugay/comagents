package com.comagents.agentproxy.domain.agent_context;

import com.comagents.commons.domain.agent_context.AgentContextItemType;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredAgentContextItemTest {

    @Test
    void namesAreTrimmed() {
        assertTrue(new RequiredAgentContextItem(AgentContextItemType.STRING, " Focus ")
                .matches(new StringAgentContextItem("  focus \t", "security")));
    }
}
