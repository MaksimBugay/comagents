package com.comagents.commons.communication;

public interface ComagentsMessageListener<T> {

    Class<T> getPayloadClass();

    /**
     * Gets the payload of a message; the dispatcher has checked that a {@link WithSequenceId} payload carries the
     * message's sequence id.
     */
    void consumePayload(String sequenceId, T payload);

    /**
     * Gets the error a message carries in place of its payload; ignored unless overridden.
     */
    default void consumeError(String sequenceId, ComagentsError error) {
    }
}
