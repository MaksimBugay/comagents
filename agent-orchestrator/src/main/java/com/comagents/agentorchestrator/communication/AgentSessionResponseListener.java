package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.controller.AgentSessionResponseRegistry;
import com.comagents.agentorchestrator.controller.RunningAgentSessionRegistry;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.SessionCleanupRequest;
import com.comagents.commons.response.AgentSessionResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * The responses of finished agent sessions, as the executors send them: a session with its response is no longer
 * running ({@link RunningAgentSessionRegistry}), and its response is kept ({@link AgentSessionResponseRegistry}).
 * Once kept here, the executor that ran the session is told to delete the session's files
 * ({@link SessionCleanupRequest}); one that never gets it deletes them after its {@code agent-response.cleanup-delay}.
 */
@Singleton
public class AgentSessionResponseListener implements ComagentsMessageListener<AgentSessionResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionResponseListener.class);

    private final RunningAgentSessionRegistry runningAgentSessionRegistry;
    private final AgentSessionResponseRegistry agentSessionResponseRegistry;
    private final ComagentsMessageDeliveryService messageDeliveryService;

    public AgentSessionResponseListener(RunningAgentSessionRegistry runningAgentSessionRegistry,
                                        AgentSessionResponseRegistry agentSessionResponseRegistry,
                                        ComagentsMessageDeliveryService messageDeliveryService) {
        this.runningAgentSessionRegistry = runningAgentSessionRegistry;
        this.agentSessionResponseRegistry = agentSessionResponseRegistry;
        this.messageDeliveryService = messageDeliveryService;
    }

    @Override
    public Class<AgentSessionResponse> getPayloadClass() {
        return AgentSessionResponse.class;
    }

    @Override
    public void consumePayload(String sequenceId, AgentSessionResponse response) {
        // kept first: a fetch in between finds the session in one registry or the other
        agentSessionResponseRegistry.append(response);
        runningAgentSessionRegistry.remove(response.request().sessionId());
        LOG.info("Response of session {} received from executor {}", response.request().sessionId(),
                response.executorDeviceId());
        requestCleanup(response);
    }

    // not sent: the executor deletes the files after its cleanup delay anyway
    private void requestCleanup(AgentSessionResponse response) {
        AiAgent agent = response.request().agent();
        UUID sessionId = response.request().sessionId();
        try {
            messageDeliveryService.deliverToAddress(
                    DeliveryAddress.agentExecutor(response.executorDeviceId(), agent.getAiHarnessProvider()),
                    new SessionCleanupRequest(agent, sessionId, UUID.randomUUID().toString()));
        } catch (RuntimeException e) {
            LOG.warn("Asking executor {} to delete the files of session {} failed: {}", response.executorDeviceId(),
                    sessionId, e.getMessage());
        }
    }
}
