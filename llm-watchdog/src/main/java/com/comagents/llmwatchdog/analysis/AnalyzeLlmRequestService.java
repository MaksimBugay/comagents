package com.comagents.llmwatchdog.analysis;

/**
 * Looks at every call to the LLM provider before it is sent, and decides whether it goes on: the place for the
 * security analysis of what agents send to the model.
 */
public interface AnalyzeLlmRequestService {

    /** May block: the proxy calls it off the event loop. */
    LlmRequestVerdict analyze(LlmRequest request);
}
