package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessageDispatcher;
import com.comagents.commons.communication.ComagentsMessageListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Takes the comagents messages an orchestrator sends to the executor and passes each to the listeners of its payload
 * class, on a virtual thread of its own: a start request clones and copies files, and the Pushca client's receiving
 * thread must not wait for that.
 */
@Singleton
public class ComagentsMessageReceiver implements Consumer<String> {

    private static final Logger LOG = LoggerFactory.getLogger(ComagentsMessageReceiver.class);

    private final ComagentsMessageDispatcher dispatcher;

    public ComagentsMessageReceiver(
            // all listeners: a List injection, which the IDE's Micronaut inspection doesn't recognize for a wildcard type
            @SuppressWarnings("MnInjectionPoints") List<ComagentsMessageListener<?>> listeners) {
        this.dispatcher = new ComagentsMessageDispatcher(listeners);
    }

    @Override
    public void accept(String comagentsMessageString) {
        Thread.ofVirtual().name("comagents-message").start(() -> dispatch(comagentsMessageString));
    }

    // a listener answers the orchestrator itself, errors included; what fails here has no one to go to
    private void dispatch(String comagentsMessageString) {
        try {
            dispatcher.dispatch(comagentsMessageString);
        } catch (RuntimeException e) {
            LOG.error("Message from Pushca not handled", e);
        }
    }
}
