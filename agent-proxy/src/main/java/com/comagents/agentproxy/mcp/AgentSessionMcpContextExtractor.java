package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands the session headers of an MCP request ({@link AgentProxyMcpServer#SESSION_ID_HEADER},
 * {@link AgentProxyMcpServer#SESSION_TOKEN_HEADER}) to the tools, which read them from their
 * {@link McpTransportContext}. Replaces Micronaut MCP's extractor, which passes on standard MCP headers only; the
 * stateless server itself reads nothing from the context.
 */
@Primary
@Singleton
public class AgentSessionMcpContextExtractor implements McpTransportContextExtractor<HttpRequest<?>> {

    private static final List<String> SESSION_HEADERS =
            List.of(AgentProxyMcpServer.SESSION_ID_HEADER, AgentProxyMcpServer.SESSION_TOKEN_HEADER);

    @Override
    public McpTransportContext extract(HttpRequest<?> request) {
        Map<String, Object> metadata = new HashMap<>();
        for (String header : SESSION_HEADERS) {
            request.getHeaders().findFirst(header).ifPresent(value -> metadata.put(header, value));
        }
        return McpTransportContext.create(metadata);
    }
}
