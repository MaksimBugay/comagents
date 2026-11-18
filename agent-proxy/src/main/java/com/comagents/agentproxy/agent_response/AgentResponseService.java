package com.comagents.agentproxy.agent_response;

import com.comagents.agentproxy.agent_setup.AgentSetupService;
import com.comagents.agentproxy.agent_setup.AgentSetupServices;
import com.comagents.agentproxy.agent_status.AgentSessionNotFoundException;
import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.serialization.JsonUtility;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out an agent's final answer for a session, with the request that started it. The first time a response is found, all of the session's files
 * are scheduled for deletion after {@code agent-response.cleanup-delay} (5 minutes by default), so the caller has
 * that long to fetch it again.
 */
@Singleton
public class AgentResponseService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseService.class);

    private final AgentSetupServices agentSetupServices;
    private final AgentStatusService agentStatusService;
    private final SessionFolders sessionFolders;
    private final TaskScheduler taskScheduler;
    private final Duration cleanupDelay;
    private final String deviceId;
    private final Set<UUID> scheduledCleanups = ConcurrentHashMap.newKeySet();

    public AgentResponseService(AgentSetupServices agentSetupServices, AgentStatusService agentStatusService,
                                SessionFolders sessionFolders,
                                @Named(TaskExecutors.SCHEDULED) TaskScheduler taskScheduler,
                                @Value("${agent-response.cleanup-delay:5m}") Duration cleanupDelay,
                                AgentProxyConf agentProxyConf) {
        this.agentSetupServices = agentSetupServices;
        this.agentStatusService = agentStatusService;
        this.sessionFolders = sessionFolders;
        this.taskScheduler = taskScheduler;
        this.cleanupDelay = cleanupDelay;
        this.deviceId = agentProxyConf.getDeviceId();
    }

    /**
     * {@link #findResponse}, or why there is none.
     *
     * @throws AgentSessionNotFoundException  when the session is unknown: never started, or already cleaned up
     * @throws AgentResponseNotReadyException when the session has no response yet (in progress, or archived)
     */
    public AgentSessionResponse getResponse(AiAgent agent, UUID sessionId) {
        return findResponse(agent, sessionId).orElseThrow(() -> noResponse(agent, sessionId));
    }

    /**
     * @return the response, or empty while the agent has not answered yet (or after the session was cleaned up, or
     *         when the session is no longer known, e.g. after a restart)
     */
    public Optional<AgentSessionResponse> findResponse(AiAgent agent, UUID sessionId) {
        Optional<AgentSessionResponse> response = readResponse(agent, sessionId);
        response.ifPresent(_ -> scheduleCleanup(agent, sessionId));
        return response;
    }

    /** {@link #findResponse} without scheduling the cleanup. */
    public Optional<AgentSessionResponse> readResponse(AiAgent agent, UUID sessionId) {
        Optional<AgentSessionAttributes> attributes = setupServiceFor(agent).attributes(sessionId);
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        Path responseFile = sessionFolders.agentResponseFile(sessionId, agent);
        try {
            AgentContext response = JsonUtility.fromJson(Files.readAllBytes(responseFile), AgentContext.class);
            return Optional.of(new AgentSessionResponse(request(agent, attributes.get()), response,
                    Instant.now().toEpochMilli(), deviceId));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + responseFile, e);
        }
    }

    // every session leaves a response when it ends, so a known session without one is still to give it
    private RuntimeException noResponse(AiAgent agent, UUID sessionId) {
        return agentStatusService.findStatus(agent, sessionId)
                .<RuntimeException>map(status -> new AgentResponseNotReadyException(agent, sessionId, status.state()))
                .orElseGet(() -> new AgentSessionNotFoundException(agent, sessionId));
    }

    private static StartAgentSessionRequest request(AiAgent agent, AgentSessionAttributes attributes) {
        return new StartAgentSessionRequest(attributes.sessionId(), agent, attributes.agentContext(),
                attributes.sequenceId(), attributes.orchestratorDeviceId(), attributes.actorAccountId(), attributes.loggingLevel(), attributes.createdAt());
    }

    private AgentSetupService setupServiceFor(AiAgent agent) {
        return agentSetupServices.forAgent(agent)
                .orElseThrow(() -> new IllegalStateException("No setup service for harness provider " + agent.getAiHarnessProvider()));
    }

    /**
     * Deletes the session's files after {@code agent-response.cleanup-delay}, once its response was handed out
     * (fetched, or sent to an orchestrator). Only the first call schedules; the set entry is dropped once the cleanup
     * has run.
     */
    public void scheduleCleanup(AiAgent agent, UUID sessionId) {
        if (scheduledCleanups.add(sessionId)) {
            LOG.info("[{} {}] response handed out, deleting session files in {}", agent.getAgentName(), sessionId, cleanupDelay);
            taskScheduler.schedule(cleanupDelay, () -> cleanUp(agent, sessionId));
        }
    }

    // an archived session is kept for a later resume, so its files stay
    private void cleanUp(AiAgent agent, UUID sessionId) {
        try {
            AgentSetupService setupService = setupServiceFor(agent);
            boolean archived = setupService.status(sessionId)
                    .map(status -> status.state() == AgentSessionState.ARCHIVED)
                    .orElse(false);
            if (archived) {
                LOG.info("[{} {}] archived, keeping session files", agent.getAgentName(), sessionId);
            } else {
                setupService.cleanUp(sessionId);
            }
        } catch (RuntimeException e) {
            LOG.error("[{} {}] deleting session files failed", agent.getAgentName(), sessionId, e);
        } finally {
            scheduledCleanups.remove(sessionId);
        }
    }
}
