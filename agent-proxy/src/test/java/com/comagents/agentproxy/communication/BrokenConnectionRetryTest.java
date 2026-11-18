package com.comagents.agentproxy.communication;

import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokenConnectionRetryTest {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void triesAgainEverySecondWhileTheConnectionIsBroken() {
        long start = System.nanoTime();

        String result = BrokenConnectionRetry.callWithRetry(() -> {
            switch (calls.incrementAndGet()) {
                case 1 -> throw new WebsocketConnectionIsNotOpenException();
                case 2 -> throw new WebsocketConnectionIsBrokenException(new IllegalStateException("no ack"));
                default -> {
                    return "done";
                }
            }
        }, Instant.now().plusSeconds(10));

        assertEquals("done", result);
        assertEquals(3, calls.get());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) >= 0, "a pause each");
    }

    @Test
    void givesUpWithTheLastFailureOnceTheDeadlinePassed() {
        WebsocketConnectionIsNotOpenException broken = new WebsocketConnectionIsNotOpenException();

        assertSame(broken, assertThrows(WebsocketConnectionIsNotOpenException.class,
                () -> BrokenConnectionRetry.runWithRetry(() -> {
                    calls.incrementAndGet();
                    throw broken;
                }, Instant.now().plusMillis(1500))));
        assertEquals(2, calls.get(), "no try after the deadline");
    }

    @Test
    void failsAtOnceForAnythingButABrokenConnection() {
        PushcaCommandException rejected = new PushcaCommandException("Send message", "no such receiver");

        assertSame(rejected, assertThrows(PushcaCommandException.class,
                () -> BrokenConnectionRetry.runWithRetry(() -> {
                    calls.incrementAndGet();
                    throw rejected;
                }, Instant.now().plusSeconds(10))));
        assertEquals(1, calls.get());
    }
}
