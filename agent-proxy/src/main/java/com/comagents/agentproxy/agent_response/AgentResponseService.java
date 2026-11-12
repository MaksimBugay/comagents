package com.comagents.agentproxy.agent_response;

import com.comagents.agentproxy.agent_setup.AgentSetupServices;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.response.AgentResponse;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out an agent's final answer for a session. The first time a response is found, all of the session's files
 * are scheduled for deletion after {@code agent-response.cleanup-delay} (5 minutes by default), so the caller has
 * that long to fetch it again.
 */
@Singleton
public class AgentResponseService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseService.class);

    private final AgentSetupServices agentSetupServices;
    private final TaskScheduler taskScheduler;
    private final Duration cleanupDelay;
    private final Set<UUID> scheduledCleanups = ConcurrentHashMap.newKeySet();

    public AgentResponseService(AgentSetupServices agentSetupServices,
                                @Named(TaskExecutors.SCHEDULED) TaskScheduler taskScheduler,
                                @Value("${agent-response.cleanup-delay:5m}") Duration cleanupDelay) {
        this.agentSetupServices = agentSetupServices;
        this.taskScheduler = taskScheduler;
        this.cleanupDelay = cleanupDelay;
    }

    /**
     * @return the response, or empty while the agent has not answered yet (or after the session was cleaned up)
     */
    public Optional<AgentResponse> findResponse(AiAgent agent, UUID sessionId) {
        Optional<AgentResponse> response = readResponse(agent, sessionId);
        response.ifPresent(_ -> scheduleCleanup(agent, sessionId));
        return response;
    }

    private static Optional<AgentResponse> readResponse(AiAgent agent, UUID sessionId) {
        Path responseFile = SessionFolders.agentResponseFile(sessionId, agent);
        try {
            long createdAt = Files.readAttributes(responseFile, BasicFileAttributes.class).creationTime().toMillis();
            return Optional.of(new AgentResponse(agent, sessionId, Files.readAllBytes(responseFile), createdAt));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + responseFile, e);
        }
    }

    // only the first fetch schedules; the set entry is dropped once the cleanup has run
    private void scheduleCleanup(AiAgent agent, UUID sessionId) {
        if (scheduledCleanups.add(sessionId)) {
            LOG.info("[{} {}] response fetched, deleting session files in {}", agent.getAgentName(), sessionId, cleanupDelay);
            taskScheduler.schedule(cleanupDelay, () -> cleanUp(agent, sessionId));
        }
    }

    private void cleanUp(AiAgent agent, UUID sessionId) {
        try {
            agentSetupServices.forAgent(agent)
                    .orElseThrow(() -> new IllegalStateException("No setup service for harness provider " + agent.getAiHarnessProvider()))
                    .cleanUp(sessionId);
        } catch (RuntimeException e) {
            LOG.error("[{} {}] deleting session files failed", agent.getAgentName(), sessionId, e);
        } finally {
            scheduledCleanups.remove(sessionId);
        }
    }
}
