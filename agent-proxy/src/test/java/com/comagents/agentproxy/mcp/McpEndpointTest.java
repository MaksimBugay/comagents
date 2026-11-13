package com.comagents.agentproxy.mcp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** agent-proxy's MCP server as Claude Code calls it: JSON-RPC over HTTP POST on {@code /mcp}. */
@MicronautTest
class McpEndpointTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void listsTheAskExpertTool() {
        String tools = post(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()), Map.of());

        assertTrue(tools.contains("\"name\":\"ask_expert\""), tools);
        assertTrue(tools.contains("\"question\""), tools);
    }

    @Test
    void refusesCallsWithoutTheRunsToken() {
        Map<String, Object> call = Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call",
                "params", Map.of("name", "ask_expert", "arguments", Map.of("question", "Which branch?")));
        Map<String, String> headers = Map.of("X-Agent-Session-Id", UUID.randomUUID().toString(),
                "X-Agent-Session-Token", "guessed");

        String response;
        try {
            response = post(call, headers);
        } catch (HttpClientResponseException e) {
            response = e.getResponse().getBody(String.class).orElse("");
        }

        assertTrue(response.contains("Unknown session or wrong session token"), response);
    }

    private String post(Map<String, Object> body, Map<String, String> headers) {
        MutableHttpRequest<?> request = HttpRequest.POST("/mcp", body)
                .header("Accept", "application/json, text/event-stream");
        headers.forEach(request::header);
        return client.toBlocking().retrieve(request, String.class);
    }
}
