package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_status.AgentExecutorInfoService;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.exception.NoAgentSetupServiceException;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;

/** Starts agent sessions for the orchestrators' start requests over Pushca. */
@Singleton
public class AgentSessionStarter {

    private final AgentSetupServices agentSetupServices;
    private final AgentExecutorInfoService agentExecutorInfoService;
    private final AgentProxyConf conf;

    public AgentSessionStarter(AgentSetupServices agentSetupServices, AgentExecutorInfoService agentExecutorInfoService,
                               AgentProxyConf conf) {
        this.agentSetupServices = agentSetupServices;
        this.agentExecutorInfoService = agentExecutorInfoService;
        this.conf = conf;
    }

    /**
     * Prepares the session and starts the agent in the background.
     *
     * @return this agent-proxy, which runs the session, with the request's sequence id and the slots left free after it
     * @throws jakarta.validation.ConstraintViolationException when the request is invalid
     * @throws NoAgentSetupServiceException                    when the agent's harness has no setup service yet
     */
    // @Valid: Micronaut validates the request before the call
    public AgentInfoResponse start(@Valid StartAgentSessionRequest request) {
        setupServiceFor(request.agent()).setup(
                request.agent(),
                new AgentSessionAttributes(
                        request.sessionId(),
                        request.agent(),
                        request.agentContext(),
                        request.sequenceId(),
                        request.originatorDeviceId(),
                        request.agentResponseWaiterDeviceId(),
                        conf.getDeviceId(),
                        request.agentCallSequence(),
                        request.actorAccountId(),
                        request.loggingLevel(),
                        request.createdAt()
                )
        );
        return agentExecutorInfoService.getInfo(request.sequenceId());
    }

    private AgentSetupService setupServiceFor(AiAgent agent) {
        return agentSetupServices.forAgent(agent).orElseThrow(() -> new NoAgentSetupServiceException(agent));
    }
}
