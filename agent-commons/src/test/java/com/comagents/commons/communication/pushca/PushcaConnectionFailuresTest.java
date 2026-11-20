package com.comagents.commons.communication.pushca;

import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushcaConnectionFailuresTest {

    @Test
    void aConnectionNotOpenOrNotAcknowledgedIsBroken() {
        assertTrue(PushcaConnectionFailures.isBrokenConnection(new WebsocketConnectionIsNotOpenException()));
        assertTrue(PushcaConnectionFailures.isBrokenConnection(
                new WebsocketConnectionIsBrokenException(new IllegalStateException("no ack"))));
    }

    @Test
    void whatPushcaRejectsIsNotABrokenConnection() {
        assertFalse(PushcaConnectionFailures.isBrokenConnection(
                new PushcaCommandException("Send message", "no such receiver")));
    }
}
