package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.agentorchestrator.exception.TooManyStartSessionRequestsException;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.domain.AgentSessionAttributes;
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
import java.util.ArrayList;
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
    private final RunningAgentSessionRegistry runningAgentSessionRegistry;
    private final AgentOrchestratorConf conf;

    public StartAgentSessionHelper(ComagentsGateway gateway, AgentDiscoveryRegistry agentDiscoveryRegistry,
                                   RunningAgentSessionRegistry runningAgentSessionRegistry,
                                   AgentOrchestratorConf conf) {
        this.gateway = gateway;
        this.agentDiscoveryRegistry = agentDiscoveryRegistry;
        this.runningAgentSessionRegistry = runningAgentSessionRegistry;
        this.conf = conf;
    }

    /**
     * Sends a start request for a new session of {@code agent} to the best executor available for it and waits for
     * its reply. Retrying is the caller's: an executor that refuses it for lack of slots is removed from the registry
     * until it reports again, so a retry goes elsewhere. The executor sends its replies and the session's response
     * to this orchestrator's own device, and the response to {@code agentResponseWaiterDeviceId} too. A started
     * session is registered in the {@link RunningAgentSessionRegistry}.
     *
     * @param sessionId                   the id of the new session
     * @param agentCallSequence           the agents that called this one, the caller last; empty or null when a person
     *                                    starts it. The started agent is appended unless it is in it already: the
     *                                    session and the agents it calls see the whole chain
     * @param agentResponseWaiterDeviceId another device that gets the session's response too; none when null or
     *                                    blank
     * @return the executor that runs the session, as it was right after taking it
     * @throws TooManyStartSessionRequestsException when no executor of the agent's harness has a free slot now
     * @throws ComagentsResponseTimeoutException    when no reply came in time
     * @throws ClaudeSessionLimitException          when the executor had no free slot after all
     * @throws RuntimeException                     why else the executor didn't start the session
     */
    public AgentInfoResponse startAgentSession(@NotNull UUID sessionId,
                                               @NotNull AiAgent agent,
                                               @Nullable AgentContext agentContext,
                                               List<String> agentCallSequence,
                                               String actorAccountId,
                                               String agentResponseWaiterDeviceId,
                                               AgentSessionLoggingLevel loggingLevel) {
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notNull(agent, "agent must not be null");
        AgentInfoResponse executor = findExecutor(agent);
        StartAgentSessionRequest request = new StartAgentSessionRequest(
                sessionId,
                agent,
                agentContext,
                UUID.randomUUID().toString(),
                conf.getDeviceId(),
                agentResponseWaiterDeviceId,
                withStartedAgent(agentCallSequence, agent),
                actorAccountId,
                loggingLevel == null ? AgentSessionLoggingLevel.INFO : loggingLevel,
                Instant.now().toEpochMilli());
        AgentInfoResponse started = startOn(executor, request);
        // its slots as they are now, before its next discovery report
        agentDiscoveryRegistry.update(started);
        runningAgentSessionRegistry.append(request.sessionId(), attributesOf(request, started.deviceId()));
        LOG.info("Session {} of {} started on executor {}", request.sessionId(), agent, started.deviceId());
        return started;
    }

    private static List<String> withStartedAgent(@Nullable List<String> agentCallSequence, AiAgent agent) {
        List<String> sequence = new ArrayList<>(agentCallSequence == null ? List.of() : agentCallSequence);
        if (!sequence.contains(agent.getAgentName())) {
            sequence.add(agent.getAgentName());
        }
        return List.copyOf(sequence);
    }

    // an executor without a free slot after all is forgotten until its next discovery report
    private AgentInfoResponse startOn(AgentInfoResponse executor, StartAgentSessionRequest request) {
        try {
            return gateway.exchange(
                    DeliveryAddress.agentExecutor(executor.deviceId(), executor.harnessProvider()),
                    request,
                    AgentInfoResponse.class
            );
        } catch (ClaudeSessionLimitException e) {
            agentDiscoveryRegistry.remove(executor.deviceId());
            throw e;
        }
    }

    private static AgentSessionAttributes attributesOf(StartAgentSessionRequest request,
                                                       String agentExecutorDeviceId) {
        return new AgentSessionAttributes(
                request.sessionId(),
                request.agent(),
                request.agentContext(),
                request.sequenceId(),
                request.originatorDeviceId(),
                request.agentResponseWaiterDeviceId(),
                agentExecutorDeviceId,
                request.agentCallSequence(),
                request.actorAccountId(),
                request.loggingLevel(),
                request.createdAt()
        );
    }

    private AgentInfoResponse findExecutor(AiAgent agent) {
        boolean oneSessionPerExecutor = conf.isOneSessionPerExecutor();
        return agentDiscoveryRegistry.getBestAvailable(agent.getAiHarnessProvider(), oneSessionPerExecutor)
                .orElseThrow(() -> new TooManyStartSessionRequestsException("No %s executor %s for %s".formatted(
                        agent.getAiHarnessProvider(), oneSessionPerExecutor ? "with nothing on it" : "with a free slot",
                        agent)));
    }
}
