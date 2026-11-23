package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Starts an agent session on the best agent executor discovered for the agent's harness: the one with most free
 * slots ({@link AgentDiscoveryRegistry}).
 */
@Singleton
public class StartAgentSessionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(StartAgentSessionHelper.class);

    private final ComagentsGateway gateway;
    private final AgentDiscoveryRegistry agentDiscoveryRegistry;
    private final AgentOrchestratorConf conf;

    public StartAgentSessionHelper(ComagentsGateway gateway, AgentDiscoveryRegistry agentDiscoveryRegistry,
                                   AgentOrchestratorConf conf) {
        this.gateway = gateway;
        this.agentDiscoveryRegistry = agentDiscoveryRegistry;
        this.conf = conf;
    }

    /**
     * Sends a start request for a new session of {@code agent} to the best executor available for it and waits for
     * its reply. Retrying is the caller's: an executor that refuses it for lack of slots is removed from the registry
     * until it reports again, so a retry goes elsewhere. The executor sends its replies to this orchestrator's own
     * device, and the session's response to {@code agentResponseWaiterDeviceId}.
     *
     * @param agentResponseWaiterDeviceId the device that gets the session's response; this orchestrator's own device
     *                                    when null or blank
     * @return the executor that runs the session, as it was right after taking it
     * @throws IllegalStateException        when no executor of the agent's harness has a free slot now, or no reply
     *                                      came in time ({@link ComagentsGateway#processRequest})
     * @throws ClaudeSessionLimitException when the executor had no free slot after all
     * @throws RuntimeException             why else the executor didn't start the session
     */
    public AgentInfoResponse startAgentSession(@NotNull AiAgent agent,
                                               @Nullable AgentContext agentContext,
                                               List<String> agentCallSequence,
                                               String actorAccountId,
                                               String agentResponseWaiterDeviceId,
                                               AgentSessionLoggingLevel loggingLevel) {
        Validate.notNull(agent, "agent must not be null");
        AgentInfoResponse executor = findExecutor(agent);
        StartAgentSessionRequest request = new StartAgentSessionRequest(
                UUID.randomUUID(),
                agent,
                agentContext,
                UUID.randomUUID().toString(),
                conf.getDeviceId(),
                agentResponseWaiterDeviceId,
                agentCallSequence,
                actorAccountId,
                loggingLevel == null ? AgentSessionLoggingLevel.INFO : loggingLevel,
                Instant.now().toEpochMilli());
        AgentInfoResponse started = startOn(executor, request);
        // its slots as they are now, before its next discovery report
        agentDiscoveryRegistry.update(started);
        LOG.info("Session {} of {} started on executor {}", request.sessionId(), agent, started.deviceId());
        return started;
    }

    // an executor without a free slot after all is forgotten until its next discovery report
    private AgentInfoResponse startOn(AgentInfoResponse executor, StartAgentSessionRequest request) {
        try {
            return gateway.processRequest(
                    DeliveryAddress.agentExecutor(executor.deviceId(), executor.harnessProvider()),
                    request,
                    AgentInfoResponse.class
            );
        } catch (ClaudeSessionLimitException e) {
            agentDiscoveryRegistry.remove(executor.deviceId());
            throw e;
        }
    }

    private AgentInfoResponse findExecutor(AiAgent agent) {
        return agentDiscoveryRegistry.getBestAvailable(agent.getAiHarnessProvider(), false)
                .orElseThrow(() -> new IllegalStateException("No %s executor with a free slot for %s"
                        .formatted(agent.getAiHarnessProvider(), agent)));
    }
}
