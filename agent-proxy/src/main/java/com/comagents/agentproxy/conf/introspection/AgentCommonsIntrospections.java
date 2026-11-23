package com.comagents.agentproxy.conf.introspection;

import com.comagents.commons.communication.ComagentsError;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AgentStatus;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.AgentContextItem;
import com.comagents.commons.domain.agent_context.AgentContextItemType;
import com.comagents.commons.domain.agent_context.FileAgentContextItem;
import com.comagents.commons.domain.agent_context.GithubRepositoryAgentContextItem;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import com.comagents.commons.response.AgentResponseReply;
import com.comagents.commons.response.AgentResponseUnavailable;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.response.AgentStatusResponse;
import io.micronaut.core.annotation.Introspected;

/**
 * Bean introspections of the agent-commons request/response and Pushca message types, which can't carry
 * {@code @Introspected} themselves: agent-commons doesn't depend on Micronaut. Request validation ({@code @Valid}) and
 * the native image ({@link IntrospectedTypesReflectionFeature}) need them.
 */
@SuppressWarnings("unused") // read by Micronaut's annotation processor, never referenced in code
@Introspected(classes = {
        StartAgentSessionRequest.class,
        AgentStatusRequest.class,
        AgentResponseRequest.class,
        AgentSessionResponse.class,
        AgentStatus.class,
        AgentStatusResponse.class,
        AgentInfoResponse.class,
        AgentResponseReply.class,
        AgentResponseUnavailable.class,
        AiAgent.class,
        AiHarnessProvider.class,
        AgentSessionState.class,
        AgentSessionLoggingLevel.class,
        AgentSessionAttributes.class,
        AgentContext.class,
        AgentContextItem.class,
        AgentContextItemType.class,
        StringAgentContextItem.class,
        FileAgentContextItem.class,
        GithubRepositoryAgentContextItem.class,
        ComagentsMessage.class,
        ComagentsError.class})
final class AgentCommonsIntrospections {

    private AgentCommonsIntrospections() {
    }
}
