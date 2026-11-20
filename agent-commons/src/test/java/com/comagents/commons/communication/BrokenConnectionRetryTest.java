package com.comagents.commons.communication;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokenConnectionRetryTest {

    // what the transport throws on a broken connection
    private static final Predicate<RuntimeException> IS_BROKEN_CONNECTION = e -> e instanceof BrokenConnection;

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void triesAgainEverySecondWhileTheConnectionIsBroken() {
        long start = System.nanoTime();

        String result = BrokenConnectionRetry.callWithRetry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new BrokenConnection();
            }
            return "done";
        }, Instant.now().plusSeconds(10), IS_BROKEN_CONNECTION);

        assertEquals("done", result);
        assertEquals(3, calls.get());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) >= 0, "a pause each");
    }

    @Test
    void givesUpWithTheLastFailureOnceTheDeadlinePassed() {
        BrokenConnection broken = new BrokenConnection();

        assertSame(broken, assertThrows(BrokenConnection.class,
                () -> BrokenConnectionRetry.runWithRetry(() -> {
                    calls.incrementAndGet();
                    throw broken;
                }, Instant.now().plusMillis(1500), IS_BROKEN_CONNECTION)));
        assertEquals(2, calls.get(), "no try after the deadline");
    }

    @Test
    void failsAtOnceForAnythingButABrokenConnection() {
        IllegalStateException rejected = new IllegalStateException("no such receiver");

        assertSame(rejected, assertThrows(IllegalStateException.class,
                () -> BrokenConnectionRetry.runWithRetry(() -> {
                    calls.incrementAndGet();
                    throw rejected;
                }, Instant.now().plusSeconds(10), IS_BROKEN_CONNECTION)));
        assertEquals(1, calls.get());
    }

    private static final class BrokenConnection extends RuntimeException {
    }
}
