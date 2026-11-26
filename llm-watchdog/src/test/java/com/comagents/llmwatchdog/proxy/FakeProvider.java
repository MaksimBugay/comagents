package com.comagents.llmwatchdog.proxy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The LLM provider, in an embedded server of its own: records what reaches it and answers as a stream. */
@Requires(property = "spec.name", value = FakeProvider.SPEC)
@Controller("/anthropic/v1")
public class FakeProvider {

    static final String SPEC = "FakeProvider";
    static final String STREAM = "event: message_start\ndata: {\"type\":\"message_start\"}\n\n";

    final List<HttpRequest<?>> requests = new CopyOnWriteArrayList<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();

    @Post(uri = "/messages", consumes = MediaType.ALL, produces = "text/event-stream")
    HttpResponse<String> messages(HttpRequest<?> request, @Body String body) {
        requests.add(request);
        bodies.add(body);
        return HttpResponse.ok(STREAM).header("request-id", "req-1");
    }
}
