package com.comagents.agentproxy.agent_status;

import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets the orchestrators discover this executor: sends a fresh {@link AgentInfoResponse} (harness, device, free slots,
 * archived sessions) to all of them every {@code agent-proxy.discovery-interval} (5 s by default).
 */
@Singleton
public class AgentDiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(AgentDiscoveryClient.class);

    private final AgentExecutorInfoService agentExecutorInfoService;
    private final ComagentsMessageDeliveryService messageDeliveryService;

    public AgentDiscoveryClient(AgentExecutorInfoService agentExecutorInfoService,
                                ComagentsMessageDeliveryService messageDeliveryService) {
        this.agentExecutorInfoService = agentExecutorInfoService;
        this.messageDeliveryService = messageDeliveryService;
    }

    // a fixed delay: the delivery blocks until sent or dropped, so infos never pile up while Pushca is down
    @Scheduled(fixedDelay = "${agent-proxy.discovery-interval:5s}", initialDelay = "${agent-proxy.discovery-interval:5s}")
    void sendAgentInfo() {
        try {
            messageDeliveryService.deliverToAddress(
                    DeliveryAddress.agentOrchestrators(),
                    agentExecutorInfoService.getInfo()
            );
        } catch (RuntimeException e) {
            LOG.warn("Sending the agent info to the orchestrators failed: {}", e.getMessage());
        }
    }
}
