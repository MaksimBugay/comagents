package com.comagents.llmwatchdog.analysis;

import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.Nullable;

/**
 * Whether a call goes on to the LLM provider.
 *
 * @param allowed whether it goes on
 * @param reason  why it doesn't, for the caller; null when it does
 */
public record LlmRequestVerdict(boolean allowed, @Nullable String reason) {

    private static final LlmRequestVerdict ALLOWED = new LlmRequestVerdict(true, null);

    public static LlmRequestVerdict allow() {
        return ALLOWED;
    }

    /**
     * @throws IllegalArgumentException when the reason is blank
     */
    public static LlmRequestVerdict reject(String reason) {
        Validate.notBlank(reason, "reason must not be blank");
        return new LlmRequestVerdict(false, reason);
    }
}
