package com.comagents.llmwatchdog.proxy;

import com.comagents.llmwatchdog.analysis.AnalyzeLlmRequestService;
import com.comagents.llmwatchdog.analysis.LlmRequest;
import com.comagents.llmwatchdog.analysis.LlmRequestVerdict;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The LLM provider's API ({@code /v1/...}, the Anthropic Messages API) as Claude Code calls it: every call is analyzed
 * ({@link AnalyzeLlmRequestService}), then forwarded to {@code llm-watchdog.anthropic-url} with this service's own
 * API key in place of the caller's, and the provider's response, streamed or not, goes back as it comes. The caller's
 * key is no secret: agent-proxy gives each Claude Code run its session id as the key. A rejected call gets 403 with
 * an Anthropic API error body, which Claude Code shows the agent.
 */
@Controller("/v1")
public class LlmProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(LlmProxyController.class);

    private static final String API_KEY_HEADER = "x-api-key";
    // the caller's own credentials stay here, and so do the headers of the hop to this service
    private static final Set<String> NOT_FORWARDED = Set.of(API_KEY_HEADER, "authorization", "host", "content-length",
            "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade", "proxy-authorization");

    private final ProxyHttpClient provider;
    private final String providerUrl;
    private final String apiKey;
    private final AnalyzeLlmRequestService analyzeLlmRequestService;

    public LlmProxyController(@Client(id = "anthropic") ProxyHttpClient provider,
                              @Value("${llm-watchdog.anthropic-url}") String providerUrl,
                              @Value("${llm-watchdog.anthropic-api-key}") String apiKey,
                              AnalyzeLlmRequestService analyzeLlmRequestService) {
        this.provider = provider;
        this.providerUrl = StringUtils.removeEnd(providerUrl, "/");
        this.apiKey = apiKey;
        this.analyzeLlmRequestService = analyzeLlmRequestService;
    }

    // the analysis may block: off the event loop
    @Post(uri = "/{+path}", consumes = MediaType.ALL, produces = MediaType.ALL)
    @ExecuteOn(TaskExecutors.BLOCKING)
    public Publisher<MutableHttpResponse<?>> post(HttpRequest<?> request, @PathVariable String path,
                                                  @Body @Nullable String body) {
        return forward(request, path, body);
    }

    @Get(uri = "/{+path}", produces = MediaType.ALL)
    @ExecuteOn(TaskExecutors.BLOCKING)
    public Publisher<MutableHttpResponse<?>> get(HttpRequest<?> request, @PathVariable String path) {
        return forward(request, path, null);
    }

    private Publisher<MutableHttpResponse<?>> forward(HttpRequest<?> request, String path, @Nullable String body) {
        LlmRequest llmRequest = new LlmRequest(callerKey(request), request.getMethod().name(), "/v1/" + path, body);
        LlmRequestVerdict verdict = analyzeLlmRequestService.analyze(llmRequest);
        if (!verdict.allowed()) {
            LOG.warn("[{}] {} {} rejected: {}", llmRequest.callerKey(), llmRequest.method(), llmRequest.path(),
                    verdict.reason());
            return Publishers.just(rejected(verdict.reason()));
        }
        return provider.proxy(toProvider(request, llmRequest.path(), body));
    }

    private MutableHttpRequest<?> toProvider(HttpRequest<?> request, String path, @Nullable String body) {
        String query = request.getUri().getRawQuery();
        MutableHttpRequest<Object> forwarded = HttpRequest.create(request.getMethod(),
                providerUrl + path + (query == null ? "" : "?" + query));
        request.getHeaders().forEach((name, values) -> {
            if (!NOT_FORWARDED.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> forwarded.header(name, value));
            }
        });
        forwarded.header(API_KEY_HEADER, apiKey);
        if (body != null) {
            forwarded.body(body);
        }
        return forwarded;
    }

    // Claude Code sends ANTHROPIC_API_KEY as x-api-key, ANTHROPIC_AUTH_TOKEN as a bearer token
    private static @Nullable String callerKey(HttpRequest<?> request) {
        String apiKey = request.getHeaders().get(API_KEY_HEADER);
        if (apiKey != null) {
            return apiKey;
        }
        return request.getHeaders().getAuthorization()
                .filter(authorization -> authorization.startsWith("Bearer "))
                .map(authorization -> authorization.substring("Bearer ".length()))
                .orElse(null);
    }

    // as the Anthropic API answers a refused call
    private static MutableHttpResponse<?> rejected(@Nullable String reason) {
        return HttpResponse.status(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body(Map.of("type", "error", "error", Map.of("type", "permission_error", "message",
                        reason == null ? "Rejected" : reason)));
    }
}
