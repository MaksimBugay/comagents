package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.PClient;

/** Opens Pushca connections. */
public interface PushcaConnector {

    /**
     * Signs {@code client} in and returns once its connection is open.
     *
     * @throws IllegalStateException when the connection cannot be opened
     */
    PushcaConnection connect(PClient client);
}
