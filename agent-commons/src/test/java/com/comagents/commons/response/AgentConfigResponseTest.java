package com.comagents.commons.response;

import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentConfigResponseTest {

    private static final ClaudeAgentConfig FORECAST =
            JsonUtility.fromJson("{\"kickOffPrompt\": \"Forecast it\"}", ClaudeAgentConfig.class);
    private static final ClaudeAgentConfig REVIEW =
            JsonUtility.fromJson("{\"kickOffPrompt\": \"Review it\"}", ClaudeAgentConfig.class);

    @Test
    void keepsTheMapsOrder() {
        Map<AiAgent, ClaudeAgentConfig> configs = new LinkedHashMap<>();
        configs.put(AiAgent.WEATHER_FORECAST, FORECAST);
        configs.put(AiAgent.CODE_REVIEW, REVIEW);

        AgentConfigResponse response = AgentConfigResponse.of(configs);

        assertEquals(List.of(new AgentConfigResponse.AgentConfig(AiAgent.WEATHER_FORECAST, FORECAST),
                new AgentConfigResponse.AgentConfig(AiAgent.CODE_REVIEW, REVIEW)), response.agentConfigs());
    }

    @Test
    void readsBackFromJson() {
        AgentConfigResponse response = AgentConfigResponse.of(Map.of(AiAgent.CODE_REVIEW, REVIEW));

        AgentConfigResponse read = JsonUtility.fromJson(JsonUtility.toJson(response), AgentConfigResponse.class);

        assertEquals(AiAgent.CODE_REVIEW, read.agentConfigs().getFirst().agent());
        assertEquals("Review it", read.agentConfigs().getFirst().config().kickOffPrompt());
    }

    @Test
    void anAgentNeedsAConfig() {
        assertThrows(NullPointerException.class, () -> new AgentConfigResponse.AgentConfig(AiAgent.CODE_REVIEW, null));
    }
}
