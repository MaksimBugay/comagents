package com.comagents.agentproxy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The llm-watchdog, in an embedded server of its own: records what reaches it and answers as the model would. */
@Requires(property = "spec.name", value = FakeLlmWatchdog.SPEC)
@Controller("/v1")
public class FakeLlmWatchdog {

    static final String SPEC = "FakeLlmWatchdog";
    static final String STREAM = "event: message_start\ndata: {\"type\":\"message_start\"}\n\n";
    static final String MODELS = "{\"data\":[]}";

    final List<HttpRequest<?>> requests = new CopyOnWriteArrayList<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();

    @Post(uri = "/messages", consumes = MediaType.ALL, produces = "text/event-stream")
    HttpResponse<String> messages(HttpRequest<?> request, @Body String body) {
        requests.add(request);
        bodies.add(body);
        return HttpResponse.ok(STREAM).header("request-id", "req-1");
    }

    @Get(uri = "/models", produces = MediaType.APPLICATION_JSON)
    HttpResponse<String> models(HttpRequest<?> request) {
        requests.add(request);
        return HttpResponse.ok(MODELS);
    }
}
