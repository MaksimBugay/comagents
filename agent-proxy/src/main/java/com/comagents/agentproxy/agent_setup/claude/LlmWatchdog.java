package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.UUID;

/**
 * The llm-watchdog, through which every Claude Code run reaches its model: it holds the LLM provider's API key,
 * analyzes each call and forwards it. A run gets the watchdog's URL as its API base and its session id as its API
 * key, which tells the watchdog the session and is worth nothing elsewhere.
 */
@Singleton
public class LlmWatchdog {

    /** Set for each run, never taken from agent-proxy's own environment. */
    static final String BASE_URL_VARIABLE = "ANTHROPIC_BASE_URL";
    static final String API_KEY_VARIABLE = "ANTHROPIC_API_KEY";

    private final String url;

    /**
     * @param url where Claude Code reaches the watchdog ({@code agent-proxy.llm-watchdog-url})
     */
    public LlmWatchdog(@Value("${agent-proxy.llm-watchdog-url}") String url) {
        this.url = url;
    }

    /** The model provider settings of the run of {@code sessionId}. */
    public Map<String, String> environment(UUID sessionId) {
        return Map.of(BASE_URL_VARIABLE, url, API_KEY_VARIABLE, sessionId.toString());
    }
}
