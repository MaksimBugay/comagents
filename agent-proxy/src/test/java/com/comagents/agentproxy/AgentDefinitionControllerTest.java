package com.comagents.agentproxy;

import com.comagents.commons.agent_definition.AgentDefinitionService;
import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.response.AgentConfigResponse;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class AgentDefinitionControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    // the agents asked for, and each one's agent.json with its name as the kick-off prompt
    @MockBean(AgentDefinitionService.class)
    AgentDefinitionService agentDefinitionService() {
        return agents -> {
            Map<AiAgent, ClaudeAgentConfig> configs = new LinkedHashMap<>();
            agents.forEach(agent -> configs.put(agent, JsonUtility.fromJson(
                    "{\"kickOffPrompt\": \"" + agent.getAgentName() + "\"}", ClaudeAgentConfig.class)));
            return configs;
        };
    }

    @Test
    void returnsTheConfigOfEachAgentAsked() {
        AgentConfigResponse response = client.toBlocking().retrieve(
                "/agent-definition/config?agents=WEATHER_FORECAST&agents=CODE_REVIEW", AgentConfigResponse.class);

        assertEquals(List.of(AiAgent.WEATHER_FORECAST, AiAgent.CODE_REVIEW),
                response.agentConfigs().stream().map(AgentConfigResponse.AgentConfig::agent).toList());
        assertEquals("weather_forecast", response.agentConfigs().getFirst().config().kickOffPrompt());
    }

    @Test
    void anUnknownAgentIsABadRequest() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve("/agent-definition/config?agents=NO_SUCH_AGENT"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }
}
