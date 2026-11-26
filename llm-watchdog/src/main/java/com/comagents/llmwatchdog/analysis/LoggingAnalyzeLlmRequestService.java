package com.comagents.llmwatchdog.analysis;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** For now: prints every call, then lets it go on. */
@Singleton
public class LoggingAnalyzeLlmRequestService implements AnalyzeLlmRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAnalyzeLlmRequestService.class);

    @Override
    public LlmRequestVerdict analyze(LlmRequest request) {
        LOG.info("[{}] {} {}: {}", request.callerKey(), request.method(), request.path(), request.body());
        return LlmRequestVerdict.allow();
    }
}
