package com.comagents.commons.communication;

public interface ComagentsMessageListener<T> {

    Class<T> getPayloadClass();

    void consumePayload(T payload);
}
