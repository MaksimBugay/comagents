package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_call.AgentCallToolDefinition;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The agents of the comagents sources as tools of agent-proxy's MCP server, as Claude Code lists and calls them. */
@MicronautTest
class AgentCallToolsTest {

    private static final Path AGENTS = Path.of("../agent/claude_code");

    @Inject
    AgentCallTools agentCallTools;

    // the MCP server's own
    @Inject
    JsonSchemaValidator schemaValidator;

    @Inject
    @Client("/")
    HttpClient client;

    @TempDir
    Path agentFolder;

    @Test
    void theAgentsExposedAsMcpAreListedWithTheirRequestSchemaAndReadOnlyHint() throws IOException {
        List<AgentCallToolDefinition> definitions = List.of(definition(AiAgent.CODE_REVIEW),
                definition(AiAgent.WEATHER_FORECAST), definition(AiAgent.INTERVIEW_INSIGHTS));

        agentCallTools.add(agentFolder, definitions);

        JsonNode tools = listTools();
        for (AgentCallToolDefinition definition : definitions) {
            JsonNode tool = find(tools, definition.agent().getAgentName());
            assertEquals(definition.toolDescription(), tool.path("description").stringValue());
            assertTrue(tool.path("description").stringValue().endsWith(
                    "The result is the agent's response, JSON matching this schema: "
                            + JsonUtility.toJson(definition.responseSchema())), "the caller knows the result's shape");
            assertEquals(definition.requestSchema(), tool.path("inputSchema"));
            assertTrue(tool.path("annotations").path("readOnlyHint").booleanValue(), "all three are read-only");
        }
        assertTrue(Files.isRegularFile(agentFolder.resolve(AgentCallTools.FILE_NAME)), "kept for a restart");
    }

    @Test
    void aChangedDefinitionReloadsTheTool() throws IOException {
        AgentCallToolDefinition definition = definition(AiAgent.WEATHER_FORECAST);
        agentCallTools.add(agentFolder, List.of(definition));

        AgentCallToolDefinition changed = new AgentCallToolDefinition(AiAgent.WEATHER_FORECAST, "Forecasts the weather",
                definition.requestSchema(), definition.responseSchema(), false);
        agentCallTools.add(agentFolder, List.of(changed));

        JsonNode tool = find(listTools(), "weather_forecast");
        assertEquals(changed.toolDescription(), tool.path("description").stringValue());
        assertFalse(tool.path("annotations").path("readOnlyHint").booleanValue());
        // back as the agent's agent.json defines it, for the other tests
        agentCallTools.add(agentFolder, List.of(definition));
        assertEquals(definition.toolDescription(), find(listTools(), "weather_forecast").path("description").stringValue());
    }

    @Test
    void aCallOfAnAgentToolReachesTheTool() throws IOException {
        agentCallTools.add(agentFolder, List.of(definition(AiAgent.WEATHER_FORECAST)));
        Map<String, Object> call = Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", Map.of(
                "name", "weather_forecast",
                "arguments", Map.of("items", List.of(Map.of("type", "STRING", "name", "location", "value", "Tartu")))));

        String response;
        try {
            response = post(call, Map.of(AgentProxyMcpServer.SESSION_ID_HEADER, UUID.randomUUID().toString(),
                    AgentProxyMcpServer.SESSION_TOKEN_HEADER, "guessed"));
        } catch (HttpClientResponseException e) {
            response = e.getResponse().getBody(String.class).orElse("");
        }

        assertTrue(response.contains("Unknown session or wrong session token"), response);
    }

    @Test
    void responsesAreValidatedAgainstTheCalledAgentsResponseSchema() throws IOException {
        Map<String, Object> schema = JsonUtility.convertToMap(definition(AiAgent.INTERVIEW_INSIGHTS).responseSchema());

        assertTrue(validate(schema, new StringAgentContextItem("insights", "Expect system design")));
        assertFalse(validate(schema, new StringAgentContextItem("report", "Expect system design")), "another item name");
    }

    @Test
    void withoutAResponseSchemaTheResponseIsTheDefaultItem() {
        ClaudeAgentConfig config = JsonUtility.fromJson("""
                {"kickOffPrompt": "Do it", "exposeAsMcp": {"tool": {"name": "code_review", "description": "Reviews"},
                 "requestSchema": {"type": "object"}}}""", ClaudeAgentConfig.class);
        Map<String, Object> schema = JsonUtility.convertToMap(AgentCallToolDefinition.of(AiAgent.CODE_REVIEW, config)
                .responseSchema());

        assertTrue(validate(schema, new StringAgentContextItem(ClaudeAgentConfig.DEFAULT_RESPONSE_ITEM, "All good")));
        assertFalse(validate(schema, new StringAgentContextItem("review", "All good")));
    }

    @Test
    void calledAgentsAreTheServersToolsOtherThanAskExpert() {
        assertEquals(List.of(AiAgent.WEATHER_FORECAST, AiAgent.INTERVIEW_INSIGHTS), AgentProxyMcpServer.calledAgents(List.of(
                "Skill", "mcp__agentproxy__ask_expert", "mcp__agentproxy__weather_forecast", "mcp__other__code_review",
                "mcp__agentproxy__interview_insights", "mcp__agentproxy__weather_forecast")));
        assertEquals("allowedTools name mcp__agentproxy__no_such_agent, but there is no agent no_such_agent",
                assertThrows(IllegalStateException.class,
                        () -> AgentProxyMcpServer.calledAgents(List.of("mcp__agentproxy__no_such_agent"))).getMessage());
    }

    @Test
    void anAgentWithoutExposeAsMcpCantBeCalled() {
        ClaudeAgentConfig config = JsonUtility.fromJson("""
                {"kickOffPrompt": "Do it"}""", ClaudeAgentConfig.class);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgentCallToolDefinition.of(AiAgent.CODE_REVIEW, config));
        assertTrue(e.getMessage().startsWith("Agent code_review has no exposeAsMcp"), e.getMessage());
    }

    @Test
    void theToolIsNamedAfterTheAgent() {
        ClaudeAgentConfig config = JsonUtility.fromJson("""
                {"kickOffPrompt": "Do it", "exposeAsMcp": {"tool": {"name": "review", "description": "Reviews"},
                 "requestSchema": {"type": "object"}}}""", ClaudeAgentConfig.class);

        assertEquals("The exposeAsMcp tool of agent code_review is named review, not after the agent",
                assertThrows(IllegalStateException.class, () -> AgentCallToolDefinition.of(AiAgent.CODE_REVIEW, config))
                        .getMessage());
    }

    // as the called agent's response comes: an agent context
    private boolean validate(Map<String, Object> schema, StringAgentContextItem item) {
        return schemaValidator.validate(schema, JsonUtility.convertToMap(new AgentContext(List.of(item)))).valid();
    }

    private static AgentCallToolDefinition definition(AiAgent agent) throws IOException {
        ClaudeAgentConfig config = JsonUtility.fromJson(
                Files.readAllBytes(AGENTS.resolve(agent.getAgentName()).resolve(ClaudeAgentConfig.FILE_NAME)),
                ClaudeAgentConfig.class);
        return AgentCallToolDefinition.of(agent, config);
    }

    private JsonNode listTools() {
        return JsonUtility.getSimpleMapper().readTree(post(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list",
                "params", Map.of()), Map.of())).path("result").path("tools");
    }

    private static JsonNode find(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").stringValue())) {
                return tool;
            }
        }
        throw new AssertionError("No tool " + name + " in " + tools);
    }

    private String post(Map<String, Object> body, Map<String, String> headers) {
        MutableHttpRequest<?> request = HttpRequest.POST("/mcp", body)
                .header("Accept", "application/json, text/event-stream");
        headers.forEach(request::header);
        return client.toBlocking().retrieve(request, String.class);
    }
}
