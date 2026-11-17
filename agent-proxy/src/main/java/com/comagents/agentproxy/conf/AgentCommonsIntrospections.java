package com.comagents.agentproxy.conf;

import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.AgentContextItem;
import com.comagents.commons.domain.agent_context.AgentContextItemType;
import com.comagents.commons.domain.agent_context.FileAgentContextItem;
import com.comagents.commons.domain.agent_context.GithubRepositoryAgentContextItem;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentResponseUnavailable;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.response.AgentStatus;
import io.micronaut.core.annotation.Introspected;

/**
 * Bean introspections of the agent-commons request/response types, which can't carry {@code @Introspected}
 * themselves: agent-commons doesn't depend on Micronaut. Request validation ({@code @Valid}) and the native image
 * ({@link IntrospectedTypesReflectionFeature}) need them.
 */
@SuppressWarnings("unused") // read by Micronaut's annotation processor, never referenced in code
@Introspected(classes = {
        StartAgentSessionRequest.class,
        AgentSessionResponse.class,
        AgentStatus.class,
        AgentResponseUnavailable.class,
        AiAgent.class,
        AgentSessionState.class,
        AgentSessionLoggingLevel.class,
        AgentContext.class,
        AgentContextItem.class,
        AgentContextItemType.class,
        StringAgentContextItem.class,
        FileAgentContextItem.class,
        GithubRepositoryAgentContextItem.class})
final class AgentCommonsIntrospections {

    private AgentCommonsIntrospections() {
    }
}
