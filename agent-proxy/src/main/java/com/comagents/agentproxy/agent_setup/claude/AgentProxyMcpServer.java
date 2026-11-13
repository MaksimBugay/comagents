package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * agent-proxy's own MCP server ({@code /mcp}) as each Claude Code run sees it: the {@code --mcp-config} that
 * points the run at it, with headers naming the session and carrying the run's token, and its tools, which
 * headless runs must have pre-allowed.
 */
@Singleton
public class AgentProxyMcpServer {

    /** The server's name in {@code --mcp-config}; Claude Code calls its tools {@code mcp__<name>__<tool>}. */
    public static final String NAME = "agentproxy";
    public static final String ASK_EXPERT_TOOL = "ask_expert";
    public static final String SESSION_ID_HEADER = "X-Agent-Session-Id";
    public static final String SESSION_TOKEN_HEADER = "X-Agent-Session-Token";

    // a call gets its answer or the "no answer" result within the answer timeout; the margin covers the rest
    private static final Duration CALL_TIMEOUT_MARGIN = Duration.ofMinutes(1);

    private final JsonMapper jsonMapper;
    private final String url;
    private final Duration callTimeout;

    /**
     * @param url           where Claude Code reaches the server ({@code agent-proxy.mcp-url})
     * @param answerTimeout how long {@value #ASK_EXPERT_TOOL} waits for an answer ({@code agent-question.answer-timeout})
     */
    public AgentProxyMcpServer(JsonMapper jsonMapper,
                               @Value("${agent-proxy.mcp-url}") String url,
                               @Value("${agent-question.answer-timeout}") Duration answerTimeout) {
        this.jsonMapper = jsonMapper;
        this.url = url;
        this.callTimeout = answerTimeout.plus(CALL_TIMEOUT_MARGIN);
    }

    /** The server's tools as Claude Code names them, for {@code --allowedTools}. */
    public List<String> allowedTools() {
        return List.of("mcp__" + NAME + "__" + ASK_EXPERT_TOOL);
    }

    /** The {@code --mcp-config} JSON of one run; next to the MCP servers the agent configures itself. */
    public String mcpConfig(UUID sessionId, String sessionToken) {
        Map<String, Object> server = Map.of(
                "type", "http",
                "url", url,
                "headers", Map.of(SESSION_ID_HEADER, sessionId.toString(), SESSION_TOKEN_HEADER, sessionToken),
                "timeout", callTimeout.toMillis());
        try {
            return jsonMapper.writeValueAsString(Map.of("mcpServers", Map.of(NAME, server)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the MCP config of session " + sessionId, e);
        }
    }
}
