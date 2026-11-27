package com.comagents.agentproxy.agent_definition.vcs;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.vcs.FakeGithub;
import com.comagents.agentproxy.vcs.VcsGithubService;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The agent.json of called agents, each downloaded alone from a fake GitHub. */
class AgentDefinitionGithubServiceTest {

    private static EmbeddedServer githubServer;
    private static StreamingHttpClient githubClient;

    private AgentDefinitionGithubService agentDefinitionService;

    @BeforeAll
    static void startGithub() {
        githubServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", FakeGithub.SPEC), Environment.TEST);
        githubClient = StreamingHttpClient.create(githubServer.getURL());
    }

    @AfterAll
    static void stopGithub() {
        githubClient.close();
        githubServer.close();
    }

    @BeforeEach
    void createService() {
        FakeGithub github = githubServer.getApplicationContext().getBean(FakeGithub.class);
        github.files.clear();
        github.files.putAll(Map.of(
                "agent/claude_code/weather_forecast/agent.json", """
                        {"kickOffPrompt": "Forecast it", "readOnly": true}""",
                "agent/claude_code/interview_insights/agent.json", """
                        {"kickOffPrompt": "Find insights"}""",
                "agent/claude_code/code_review/agent.json", "not json"));
        agentDefinitionService = new AgentDefinitionGithubService(new VcsGithubService(new AgentProxyConf("0.1",
                "https://github.com/acme/comagents.git", "token", "https://pushca.example", "pushca-key",
                AiHarnessProvider.CLAUDE_CODE, new DeviceIdExtractor("test-device", "host")), githubClient));
    }

    @Test
    void readsTheConfigOfEachAgentInOrder() {
        Map<AiAgent, ClaudeAgentConfig> configs =
                agentDefinitionService.getAgentConfig(List.of(AiAgent.WEATHER_FORECAST, AiAgent.INTERVIEW_INSIGHTS));

        assertEquals(List.of(AiAgent.WEATHER_FORECAST, AiAgent.INTERVIEW_INSIGHTS), List.copyOf(configs.keySet()));
        assertEquals("Forecast it", configs.get(AiAgent.WEATHER_FORECAST).kickOffPrompt());
        assertTrue(configs.get(AiAgent.WEATHER_FORECAST).readOnly());
        assertEquals("Find insights", configs.get(AiAgent.INTERVIEW_INSIGHTS).kickOffPrompt());
    }

    @Test
    void noAgentsNoConfigs() {
        assertTrue(agentDefinitionService.getAgentConfig(List.of()).isEmpty());
    }

    @Test
    void anUnreadableConfigFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> agentDefinitionService.getAgentConfig(List.of(AiAgent.CODE_REVIEW)));

        assertTrue(e.getMessage().contains("agent/claude_code/code_review/agent.json"), e.getMessage());
    }
}
