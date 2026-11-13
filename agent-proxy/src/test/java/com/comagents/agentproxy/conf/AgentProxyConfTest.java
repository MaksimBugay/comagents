package com.comagents.agentproxy.conf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class AgentProxyConfTest {

    @Inject
    AgentProxyConf conf;

    @Inject
    ApplicationContext context;

    // the default holds a colon (https:), which Micronaut cuts at unless quoted
    @Test
    void pushcaApiUrlDefaultKeepsItsScheme() {
        assertEquals("https://secure.fileshare.ovh/pushca", conf.getPushcaApiUrl());
    }

    @Test
    void pushcaChatUrlDefaultKeepsItsScheme() {
        assertEquals("https://secure.fileshare.ovh/chat-room.html", context.getRequiredProperty("pushca.chat-url", String.class));
    }
}
