package com.comagents.agentproxy.communication.pushca;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.pushca.PushcaConnectionFactory;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * The {@link PushcaConnectionFactory} for the Pushca server {@code pushca.api-url}, with the organization's API key
 * {@code pushca.api-key}.
 */
@Factory
public class PushcaConnectionFactoryProvider {

    // @Primary for the IDE, which doesn't know @Replaces and also sees the tests' FakePushcaConnectionFactory
    @Primary
    @Singleton
    public PushcaConnectionFactory pushcaConnectionFactory(
            AgentProxyConf conf, @Value("${pushca.connect-timeout:10s}") Duration connectTimeout) {
        return new PushcaConnectionFactory(conf.getPushcaApiUrl(), conf.getPushcaApiKey(), connectTimeout);
    }
}
