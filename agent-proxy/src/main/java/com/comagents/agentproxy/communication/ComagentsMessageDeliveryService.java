package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.WithSequenceId;

/**
 * Sends payloads, requests and responses alike, as {@link com.comagents.commons.communication.ComagentsMessage}s to
 * other devices; an error goes in place of a payload that couldn't be made.
 */
public interface ComagentsMessageDeliveryService {

    /**
     * Sends {@code payload} to the device {@code deviceId} as a message with the id {@code sequenceId}, by which the
     * receiver matches it: a response carries the sequence id of its request.
     */
    <T> void deliverToDevice(String deviceId, String sequenceId, T payload);

    /** Sends {@code payload} with its own sequence id as the message id. */
    default <T extends WithSequenceId> void deliverToDevice(String deviceId, T payload) {
        deliverToDevice(deviceId, payload.sequenceId(), payload);
    }

    /**
     * Sends {@code error} to the device {@code deviceId} in place of a {@code payloadClass} payload: with the id
     * {@code sequenceId}, by which the receiver matches it, and the payload class, so it reaches the receiver's
     * listener for that payload.
     */
    void deliverErrorToDevice(String deviceId, String sequenceId, Class<?> payloadClass, Throwable error);
}
