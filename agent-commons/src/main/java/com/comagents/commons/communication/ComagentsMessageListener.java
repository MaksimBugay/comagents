package com.comagents.commons.communication;

public interface ComagentsMessageListener<T> {

    Class<T> getPayloadClass();

    void consumePayload(T payload);

    /**
     * Gets the error a message carries in place of its payload; ignored unless overridden.
     */
    default void consumeError(ComagentsError error) {
    }
}
