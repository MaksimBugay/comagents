package com.comagents.agentproxy;

import com.comagents.agentproxy.agent_setup.AgentSessionJwtRegistry;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
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
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The LLM hop: the model endpoint of every Claude Code run ({@code ANTHROPIC_BASE_URL=<agent-proxy.llm-hop-url>/<session
 * id>}). Each call goes on to the llm-watchdog as it came, with the session's JWT ({@link AgentSessionJwtRegistry}) as
 * its bearer token when the session has one, and the watchdog's response, streamed or not, comes back as it comes. The
 * JWT never reaches Claude Code. Only callers on this host are served: the runs, not other services.
 */
@Controller("/llm/{sessionId}")
public class LlmHopController {

    private static final String AUTHORIZATION_HEADER = "authorization";
    // only the hop sets the bearer token, and the headers of the hop to this service stay here
    private static final Set<String> NOT_FORWARDED = Set.of(AUTHORIZATION_HEADER, "host", "content-length",
            "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade", "proxy-authorization");

    private final ProxyHttpClient llmWatchdog;
    private final String llmWatchdogUrl;
    private final AgentSessionJwtRegistry agentSessionJwtRegistry;

    public LlmHopController(@Client(id = "llm-watchdog") ProxyHttpClient llmWatchdog,
                            @Value("${agent-proxy.llm-watchdog-url}") String llmWatchdogUrl,
                            AgentSessionJwtRegistry agentSessionJwtRegistry) {
        this.llmWatchdog = llmWatchdog;
        this.llmWatchdogUrl = Strings.CS.removeEnd(llmWatchdogUrl, "/");
        this.agentSessionJwtRegistry = agentSessionJwtRegistry;
    }

    @Post(uri = "/{+path}", consumes = MediaType.ALL, produces = MediaType.ALL)
    public Publisher<MutableHttpResponse<?>> post(HttpRequest<?> request, @PathVariable UUID sessionId,
                                                  @PathVariable String path, @Body @Nullable String body) {
        return forward(request, sessionId, path, body);
    }

    @Get(uri = "/{+path}", produces = MediaType.ALL)
    public Publisher<MutableHttpResponse<?>> get(HttpRequest<?> request, @PathVariable UUID sessionId,
                                                 @PathVariable String path) {
        return forward(request, sessionId, path, null);
    }

    private Publisher<MutableHttpResponse<?>> forward(HttpRequest<?> request, UUID sessionId, String path,
                                                      @Nullable String body) {
        if (!fromThisHost(request)) {
            return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN));
        }
        return llmWatchdog.proxy(toLlmWatchdog(request, sessionId, path, body));
    }

    private MutableHttpRequest<?> toLlmWatchdog(HttpRequest<?> request, UUID sessionId, String path,
                                                @Nullable String body) {
        String query = request.getUri().getRawQuery();
        MutableHttpRequest<Object> forwarded = HttpRequest.create(request.getMethod(),
                llmWatchdogUrl + "/" + path + (query == null ? "" : "?" + query));
        request.getHeaders().forEach((name, values) -> {
            if (!NOT_FORWARDED.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> forwarded.header(name, value));
            }
        });
        agentSessionJwtRegistry.get(sessionId).ifPresent(forwarded::bearerAuth);
        if (body != null) {
            forwarded.body(body);
        }
        return forwarded;
    }

    private static boolean fromThisHost(HttpRequest<?> request) {
        InetAddress address = request.getRemoteAddress().getAddress();
        return address != null && address.isLoopbackAddress();
    }
}
