package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Strings;

import java.util.Map;
import java.util.UUID;

/**
 * The llm-watchdog, through which every Claude Code run reaches its model: it holds the LLM provider's API key,
 * analyzes each call and forwards it. A run reaches it through agent-proxy's LLM hop ({@code LlmHopController}), which
 * adds the session's JWT: the run gets the hop's URL for its session as its API base and its session id as its API
 * key, which tells the watchdog the session and is worth nothing elsewhere.
 */
@Singleton
public class LlmWatchdog {

    /** Set for each run, never taken from agent-proxy's own environment. */
    static final String BASE_URL_VARIABLE = "ANTHROPIC_BASE_URL";
    static final String API_KEY_VARIABLE = "ANTHROPIC_API_KEY";

    private final String llmHopUrl;

    /**
     * @param llmHopUrl where Claude Code reaches the LLM hop, without the session id ({@code agent-proxy.llm-hop-url})
     */
    public LlmWatchdog(@Value("${agent-proxy.llm-hop-url}") String llmHopUrl) {
        this.llmHopUrl = Strings.CS.removeEnd(llmHopUrl, "/");
    }

    /** The model provider settings of the run of {@code sessionId}. */
    public Map<String, String> environment(UUID sessionId) {
        return Map.of(BASE_URL_VARIABLE, llmHopUrl + "/" + sessionId, API_KEY_VARIABLE, sessionId.toString());
    }
}
