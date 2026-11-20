package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_status.AgentInfoService;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfo;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;

/** Starts agent sessions, whoever asks: the HTTP API or an orchestrator over Pushca. */
@Singleton
public class AgentSessionStarter {

    private final AgentSetupServices agentSetupServices;
    private final AgentInfoService agentInfoService;

    public AgentSessionStarter(AgentSetupServices agentSetupServices, AgentInfoService agentInfoService) {
        this.agentSetupServices = agentSetupServices;
        this.agentInfoService = agentInfoService;
    }

    /**
     * Prepares the session and starts the agent in the background.
     *
     * @return this agent-proxy, which runs the session, with the request's sequence id and the slots left free after it
     * @throws jakarta.validation.ConstraintViolationException when the request is invalid
     * @throws NoAgentSetupServiceException                    when the agent's harness has no setup service yet
     */
    // @Valid: Micronaut validates the request before the call, as the controller does for its body
    public AgentInfo start(@Valid StartAgentSessionRequest request) {
        setupServiceFor(request.agent()).setup(
                request.agent(),
                new AgentSessionAttributes(
                        request.sessionId(),
                        request.agentContext(),
                        request.sequenceId(),
                        request.orchestratorDeviceId(),
                        request.actorAccountId(),
                        request.loggingLevel(),
                        request.createdAt()
                )
        );
        return agentInfoService.getInfo(request.agent().getAgentName(), request.sequenceId());
    }

    private AgentSetupService setupServiceFor(AiAgent agent) {
        return agentSetupServices.forAgent(agent).orElseThrow(() -> new NoAgentSetupServiceException(agent));
    }
}
