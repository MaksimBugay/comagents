package com.comagents.agentproxy.conf.introspection;

import com.comagents.commons.communication.ComagentsError;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.response.AgentInfo;
import com.comagents.commons.response.AgentResponseReply;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.response.AgentStatusResponse;
import io.micronaut.core.beans.BeanIntrospector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// the native image registers only introspected types for reflection, which Jackson needs for records
class AgentCommonsIntrospectionsTest {

    @Test
    void typesSentOverPushcaAreIntrospected() {
        for (Class<?> type : List.of(ComagentsMessage.class, ComagentsError.class, AgentSessionResponse.class,
                AgentStatusRequest.class, AgentStatusResponse.class, AgentResponseRequest.class,
                AgentResponseReply.class, AgentInfo.class, AiHarnessProvider.class)) {
            assertTrue(BeanIntrospector.SHARED.findIntrospection(type).isPresent(), type.getName());
        }
    }
}
