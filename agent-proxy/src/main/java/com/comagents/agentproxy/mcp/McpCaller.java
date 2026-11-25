package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSession;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import io.modelcontextprotocol.common.McpTransportContext;

import java.util.Optional;
import java.util.UUID;

/**
 * The running session whose Claude Code run sent an MCP request: the request must carry its session id and its MCP
 * token (see {@link AgentProxyMcpServer}).
 */
final class McpCaller {

    private McpCaller() {
    }

    static Optional<ClaudeAgentSession> find(ClaudeAgentSessionRegistry sessions, McpTransportContext context) {
        String token = header(context, AgentProxyMcpServer.SESSION_TOKEN_HEADER);
        return parseSessionId(header(context, AgentProxyMcpServer.SESSION_ID_HEADER))
                .flatMap(sessions::findSession)
                .filter(session -> !session.getState().isFinished() && session.acceptsMcpToken(token));
    }

    private static Optional<UUID> parseSessionId(String value) {
        try {
            return Optional.ofNullable(value).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String header(McpTransportContext context, String name) {
        return context.get(name) instanceof String value ? value : null;
    }
}
