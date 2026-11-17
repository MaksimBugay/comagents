package com.comagents.commons.communication;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableMap;

public class ComagentsMessageDispatcher {

    private final Map<String, Route<?>> routesByPayloadClassName;

    public ComagentsMessageDispatcher(List<ComagentsMessageListener<?>> listeners) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners must not be null");
        }
        this.routesByPayloadClassName = listeners.stream()
                .collect(groupingBy(ComagentsMessageListener::getPayloadClass))
                .entrySet().stream()
                .collect(toUnmodifiableMap(entry -> entry.getKey().getName(),
                        entry -> route(entry.getValue())));
    }

    /**
     * Passes the payload to every listener of the message's payload class, or the error the message carries in place
     * of it; the payload of a message nobody listens to is not read. A failing listener does not stop the others: once
     * all have run, the first failure is rethrown with the rest as suppressed exceptions.
     */
    public void dispatch(String comagentsMessageString) {
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(comagentsMessageString);
        Route<?> route = routesByPayloadClassName.get(message.payloadClassName());
        if (route != null) {
            dispatch(route, message);
        }
    }

    private static <T> void dispatch(Route<T> route, ComagentsMessage message) {
        ComagentsError error = message.error();
        // read once for all listeners
        T payload = error == null ? payload(message) : null;
        RuntimeException failure = null;
        for (ComagentsMessageListener<T> listener : route.listeners()) {
            try {
                deliver(listener, message.id(), payload, error);
            } catch (RuntimeException e) {
                failure = withSuppressed(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T payload(ComagentsMessage message) {
        // the route was found by the class the message names, and the payload is read as that class: a T
        return (T) message.comagentsMessagePayload().payload();
    }

    private static <T> void deliver(ComagentsMessageListener<T> listener, String sequenceId, T payload,
                                    ComagentsError error) {
        if (error != null) {
            listener.consumeError(sequenceId, error);
        } else {
            listener.consumePayload(sequenceId, payload);
        }
    }

    private static RuntimeException withSuppressed(RuntimeException failure, RuntimeException next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    @SuppressWarnings("unchecked")
    private static <T> Route<T> route(List<ComagentsMessageListener<?>> listeners) {
        // listeners are grouped by their own payload class, so all of them take a T
        return new Route<>(List.copyOf((List<ComagentsMessageListener<T>>) (List<?>) listeners));
    }

    private record Route<T>(List<ComagentsMessageListener<T>> listeners) {
    }
}
