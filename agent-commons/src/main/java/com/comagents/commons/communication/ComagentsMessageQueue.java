package com.comagents.commons.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The comagents messages waiting to go to other devices, in the order they came: one connection sends them one by
 * one, so a single {@link ComagentsQueueConsumer} takes them.
 */
public class ComagentsMessageQueue {

    // unbounded: adding never blocks, taking blocks until there is a message
    private final BlockingQueue<QueuedComagentsMessage> messages = new LinkedBlockingQueue<>();

    public void put(QueuedComagentsMessage message) {
        messages.add(message);
    }

    /** The next message; blocks until there is one. */
    public QueuedComagentsMessage take() throws InterruptedException {
        return messages.take();
    }

    /** Removes and returns the messages waiting now. */
    public List<QueuedComagentsMessage> drain() {
        List<QueuedComagentsMessage> waiting = new ArrayList<>();
        messages.drainTo(waiting);
        return waiting;
    }
}
