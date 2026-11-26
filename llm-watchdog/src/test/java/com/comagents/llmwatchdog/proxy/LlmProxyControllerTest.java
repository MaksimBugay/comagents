package com.comagents.llmwatchdog.proxy;

import com.comagents.llmwatchdog.analysis.AnalyzeLlmRequestService;
import com.comagents.llmwatchdog.analysis.LlmRequest;
import com.comagents.llmwatchdog.analysis.LlmRequestVerdict;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Claude Code's calls through the watchdog to a fake LLM provider. */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LlmProxyControllerTest implements TestPropertyProvider {

    private static final String MESSAGES = """
            {"model":"claude-opus-5-5","max_tokens":1024,"stream":true,"messages":[{"role":"user","content":"Hi"}]}""";

    private EmbeddedServer providerServer;

    @Inject
    @Client("/")
    HttpClient client;

    // what the analysis saw, and its verdict
    private final List<LlmRequest> analyzed = new CopyOnWriteArrayList<>();
    private Function<LlmRequest, LlmRequestVerdict> verdict = _ -> LlmRequestVerdict.allow();

    @MockBean(AnalyzeLlmRequestService.class)
    AnalyzeLlmRequestService analyzeLlmRequestService() {
        return request -> {
            analyzed.add(request);
            return verdict.apply(request);
        };
    }

    @Override
    public Map<String, String> getProperties() {
        providerServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", FakeProvider.SPEC,
                "llm-watchdog.anthropic-api-key", "unused"));
        return Map.of("llm-watchdog.anthropic-url", providerServer.getURL() + "/anthropic");
    }

    @AfterAll
    void stopProvider() {
        providerServer.close();
    }

    @BeforeEach
    void reset() {
        analyzed.clear();
        provider().requests.clear();
        provider().bodies.clear();
        verdict = _ -> LlmRequestVerdict.allow();
    }

    @Test
    void forwardsTheCallWithTheServicesOwnKeyAndReturnsTheProvidersResponse() {
        HttpResponse<String> response = client.toBlocking().exchange(messages("session-1")
                .header("anthropic-version", "2023-06-01"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(FakeProvider.STREAM, response.body());
        assertEquals("req-1", response.header("request-id"), "the provider's headers come back");
        HttpRequest<?> received = provider().requests.getFirst();
        assertEquals("test-provider-key", received.getHeaders().get("x-api-key"), "the service's key, not the caller's");
        assertEquals("2023-06-01", received.getHeaders().get("anthropic-version"));
        assertEquals("true", received.getParameters().get("beta"), "the query goes on too");
        assertEquals(MESSAGES, provider().bodies.getFirst());
        assertEquals(List.of(new LlmRequest("session-1", "POST", "/v1/messages", MESSAGES)), analyzed);
    }

    @Test
    void aBearerTokenIsTheCallerKeyToo() {
        client.toBlocking().exchange(HttpRequest.POST("/v1/messages", MESSAGES)
                .contentType(MediaType.APPLICATION_JSON).bearerAuth("session-2"), String.class);

        assertEquals("session-2", analyzed.getFirst().callerKey());
        assertNull(provider().requests.getFirst().getHeaders().get("authorization"), "the caller's credentials stay here");
    }

    @Test
    void aRejectedCallNeverReachesTheProvider() {
        verdict = _ -> LlmRequestVerdict.reject("Suspicious request");

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(messages("session-1"), String.class));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        String body = e.getResponse().getBody(String.class).orElse("");
        assertTrue(body.contains("\"type\":\"permission_error\"") && body.contains("Suspicious request"), body);
        assertTrue(provider().requests.isEmpty());
    }

    private static MutableHttpRequest<String> messages(String callerKey) {
        return HttpRequest.POST("/v1/messages?beta=true", MESSAGES)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", callerKey);
    }

    private FakeProvider provider() {
        return providerServer.getApplicationContext().getBean(FakeProvider.class);
    }
}
