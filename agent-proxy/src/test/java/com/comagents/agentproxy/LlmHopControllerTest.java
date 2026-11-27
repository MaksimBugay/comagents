package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.AgentSessionJwtRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Claude Code's calls through the LLM hop to a fake llm-watchdog. */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LlmHopControllerTest implements TestPropertyProvider {

    private static final String MESSAGES = """
            {"model":"claude-opus-5-5","max_tokens":1024,"stream":true,"messages":[{"role":"user","content":"Hi"}]}""";

    private final UUID sessionId = UUID.randomUUID();

    private EmbeddedServer llmWatchdogServer;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    AgentSessionJwtRegistry agentSessionJwtRegistry;

    @Override
    public Map<String, String> getProperties() {
        llmWatchdogServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", FakeLlmWatchdog.SPEC),
                Environment.TEST);
        return Map.of("agent-proxy.llm-watchdog-url", llmWatchdogServer.getURL().toString());
    }

    @AfterAll
    void stopLlmWatchdog() {
        llmWatchdogServer.close();
    }

    @BeforeEach
    void reset() {
        llmWatchdog().requests.clear();
        llmWatchdog().bodies.clear();
    }

    @AfterEach
    void forgetJwt() {
        agentSessionJwtRegistry.remove(sessionId);
    }

    @Test
    void forwardsTheCallWithTheSessionsJwtAndReturnsTheWatchdogsResponse() {
        agentSessionJwtRegistry.store(sessionId, "jwt-1");

        HttpResponse<String> response = client.toBlocking().exchange(messages(), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(FakeLlmWatchdog.STREAM, response.body());
        assertEquals("req-1", response.header("request-id"), "the watchdog's headers come back");
        HttpRequest<?> received = llmWatchdog().requests.getFirst();
        assertEquals("Bearer jwt-1", received.getHeaders().get("authorization"));
        assertEquals(sessionId.toString(), received.getHeaders().get("x-api-key"), "the session id goes on as the key");
        assertEquals("2023-06-01", received.getHeaders().get("anthropic-version"));
        assertEquals("true", received.getParameters().get("beta"), "the query goes on too");
        assertEquals(MESSAGES, llmWatchdog().bodies.getFirst());
    }

    @Test
    void withoutAJwtNoBearerTokenGoesOn() {
        client.toBlocking().exchange(messages().bearerAuth("forged"), String.class);

        assertNull(llmWatchdog().requests.getFirst().getHeaders().get("authorization"),
                "only the hop sets the bearer token");
    }

    @Test
    void forwardsGetCallsToo() {
        agentSessionJwtRegistry.store(sessionId, "jwt-1");

        String models = client.toBlocking().retrieve(HttpRequest.GET("/llm/" + sessionId + "/v1/models"));

        assertEquals(FakeLlmWatchdog.MODELS, models);
        assertEquals("Bearer jwt-1", llmWatchdog().requests.getFirst().getHeaders().get("authorization"));
    }

    private MutableHttpRequest<String> messages() {
        return HttpRequest.POST("/llm/" + sessionId + "/v1/messages?beta=true", MESSAGES)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", sessionId.toString())
                .header("anthropic-version", "2023-06-01");
    }

    private FakeLlmWatchdog llmWatchdog() {
        return llmWatchdogServer.getApplicationContext().getBean(FakeLlmWatchdog.class);
    }
}
