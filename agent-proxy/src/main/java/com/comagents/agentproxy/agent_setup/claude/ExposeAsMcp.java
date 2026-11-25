package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.core.annotation.Introspected;
import tools.jackson.databind.JsonNode;

/**
 * {@code exposeAsMcp} in an agent's {@code agent.json}: how other agents call it as a tool of agent-proxy's MCP
 * server, when their {@code allowedTools} name {@code mcp__agentproxy__<agent name>}.
 *
 * @param tool          the tool: its name (the agent's name) and when it is worth calling
 * @param requestSchema the JSON Schema of the tool's input: the agent context ({@code {"items": [...]}}) of the
 *                      called agent's start request
 */
@Introspected
public record ExposeAsMcp(Tool tool, JsonNode requestSchema) {

    /**
     * @param name        the tool's name: the agent's name
     * @param description when it is worth calling the agent, for the calling agent's model
     */
    @Introspected
    public record Tool(String name, String description) {
    }
}
