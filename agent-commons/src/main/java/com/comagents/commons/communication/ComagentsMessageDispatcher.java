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
                        entry -> route(entry.getKey(), entry.getValue())));
    }

    /**
     * Passes the payload to every listener of its class; a message nobody listens to is ignored without loading
     * its payload class. A failing listener does not stop the others: once all have run, the first failure is
     * rethrown with the rest as suppressed exceptions.
     */
    public void dispatch(String comagentsMessageString) {
        Route<?> route = routesByPayloadClassName.get(ComagentsMessage.payloadClassName(comagentsMessageString));
        if (route != null) {
            dispatch(route, comagentsMessageString);
        }
    }

    private static <T> void dispatch(Route<T> route, String comagentsMessageString) {
        T payload = ComagentsMessage.fromMessageString(comagentsMessageString, route.payloadClass()).payload();
        RuntimeException failure = null;
        for (ComagentsMessageListener<T> listener : route.listeners()) {
            try {
                listener.consumePayload(payload);
            } catch (RuntimeException e) {
                failure = withSuppressed(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
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
    private static <T> Route<T> route(Class<T> payloadClass, List<ComagentsMessageListener<?>> listeners) {
        // listeners are grouped by their own payload class, so all of them take a T
        return new Route<>(payloadClass, List.copyOf((List<ComagentsMessageListener<T>>) (List<?>) listeners));
    }

    private record Route<T>(Class<T> payloadClass, List<ComagentsMessageListener<T>> listeners) {
    }
}
