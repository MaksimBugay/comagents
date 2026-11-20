package com.comagents.commons.communication.pushca;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;
import com.comagents.commons.communication.BrokenConnectionRetry;

/** How the Pushca client fails, for {@link BrokenConnectionRetry}. */
public final class PushcaConnectionFailures {

    private PushcaConnectionFailures() {
    }

    /**
     * Whether the Pushca client threw {@code e} for a command on a broken connection: not open, or not acknowledged by
     * Pushca.
     */
    public static boolean isBrokenConnection(RuntimeException e) {
        return e instanceof WebsocketConnectionIsNotOpenException || e instanceof WebsocketConnectionIsBrokenException;
    }
}
