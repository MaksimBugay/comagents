package com.comagents.commons.communication;

public record ComagentsMessage<T>(Class<T> payloadClass, T payload) {
}
