package com.comagents.llmwatchdog.analysis;

import org.jspecify.annotations.Nullable;

/**
 * A call to the LLM provider as Claude Code sent it, before it goes on.
 *
 * @param callerKey the key the caller sent in place of the provider's: the agent session's id; null when it sent none
 * @param method    the HTTP method
 * @param path      the provider API path, e.g. {@code /v1/messages}
 * @param body      the request body, e.g. the Messages API request with the whole conversation; null for none
 */
public record LlmRequest(@Nullable String callerKey, String method, String path, @Nullable String body) {
}
