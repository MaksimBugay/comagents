package com.comagents.agentproxy.agent_call;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ExposeAsMcp;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.core.annotation.Introspected;
import tools.jackson.databind.JsonNode;

/**
 * An agent as a tool of agent-proxy's MCP server, which other agents call: from the {@code exposeAsMcp},
 * {@code responseSchema} and {@code readOnly} of its {@code agent.json}.
 *
 * @param agent          the called agent; the tool is named after it
 * @param description    when it is worth calling the agent
 * @param requestSchema  the JSON Schema of the tool's input: the called agent's agent context
 * @param responseSchema the JSON Schema of the tool's result: the called agent's response, as its {@code agent.json}
 *                       defines it, or else the one {@code STRING} item {@value ClaudeAgentConfig#DEFAULT_RESPONSE_ITEM}
 *                       a run without a response schema answers with
 * @param readOnly       the agent changes nothing: the tool's {@code readOnlyHint}
 */
@Introspected
public record AgentCallToolDefinition(AiAgent agent, String description, JsonNode requestSchema,
                                      JsonNode responseSchema, boolean readOnly) {

    // what a run without a responseSchema answers with: its final text as one STRING item
    private static final JsonNode DEFAULT_RESPONSE_SCHEMA = JsonUtility.getSimpleMapper().readTree("""
            {"type": "object", "required": ["items"], "additionalProperties": false,
             "properties": {"items": {"type": "array", "minItems": 1, "maxItems": 1,
              "items": {"type": "object", "required": ["type", "name", "value"], "additionalProperties": false,
               "properties": {"type": {"const": "STRING"}, "name": {"const": "%s"}, "value": {"type": "string"}}}}}}"""
            .formatted(ClaudeAgentConfig.DEFAULT_RESPONSE_ITEM));

    /**
     * @throws IllegalStateException when the agent's {@code agent.json} has no usable {@code exposeAsMcp}: the agent
     *                               can't be called by other agents
     */
    public static AgentCallToolDefinition of(AiAgent agent, ClaudeAgentConfig config) {
        ExposeAsMcp exposeAsMcp = config.exposeAsMcp();
        String agentName = agent.getAgentName();
        if (exposeAsMcp == null || exposeAsMcp.tool() == null || exposeAsMcp.requestSchema() == null) {
            throw new IllegalStateException("Agent " + agentName + " has no exposeAsMcp with a tool and a "
                    + "requestSchema in its " + ClaudeAgentConfig.FILE_NAME + ": other agents can't call it");
        }
        if (!agentName.equals(exposeAsMcp.tool().name())) {
            throw new IllegalStateException("The exposeAsMcp tool of agent " + agentName + " is named "
                    + exposeAsMcp.tool().name() + ", not after the agent");
        }
        if (exposeAsMcp.tool().description() == null || exposeAsMcp.tool().description().isBlank()) {
            throw new IllegalStateException("The exposeAsMcp tool of agent " + agentName + " has no description");
        }
        if (!exposeAsMcp.requestSchema().isObject()) {
            throw new IllegalStateException("The exposeAsMcp requestSchema of agent " + agentName + " is not an object");
        }
        return new AgentCallToolDefinition(agent, exposeAsMcp.tool().description(), exposeAsMcp.requestSchema(),
                config.responseSchema() != null ? config.responseSchema() : DEFAULT_RESPONSE_SCHEMA, config.readOnly());
    }

    /** The tool's description as the calling agent's model reads it: when to call, and the shape of the result. */
    public String toolDescription() {
        return description + "\n\nThe result is the agent's response, JSON matching this schema: "
                + JsonUtility.toJson(responseSchema);
    }
}
