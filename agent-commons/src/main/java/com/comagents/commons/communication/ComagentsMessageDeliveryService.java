package com.comagents.commons.communication;

/**
 * Sends payloads, requests and responses alike, as {@link ComagentsMessage}s to other devices; an
 * error goes in place of a payload that couldn't be made.
 */
public interface ComagentsMessageDeliveryService {

    /**
     * Sends {@code payload} to {@code address} as a message with the id {@code sequenceId}, by which the receiver
     * matches it: a response carries the sequence id of its request.
     */
    <T> void deliverToDevice(DeliveryAddress address, String sequenceId, T payload);

    /**
     * Sends {@code payload} with its own sequence id as the message id.
     */
    default <T extends WithSequenceId> void deliverToDevice(DeliveryAddress address, T payload) {
        deliverToDevice(address, payload.sequenceId(), payload);
    }

    /**
     * Sends {@code error} to {@code address} in place of a {@code payloadClass} payload: with the id
     * {@code sequenceId}, by which the receiver matches it, and the payload class, so it reaches the receiver's
     * listener for that payload.
     */
    void deliverErrorToDevice(DeliveryAddress address, String sequenceId, Class<?> payloadClass, Throwable error);
}
