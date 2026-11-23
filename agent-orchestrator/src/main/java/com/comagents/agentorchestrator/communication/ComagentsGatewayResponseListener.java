package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.ComagentsError;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.WithSequenceId;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public abstract class ComagentsGatewayResponseListener<R extends WithSequenceId> implements ComagentsMessageListener<R> {

    private final ComagentsGateway gateway;

    protected ComagentsGatewayResponseListener(ComagentsGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void consumePayload(String sequenceId, R payload) {
        gateway.completeWithResponse(sequenceId, payload);
    }

    @Override
    public void consumeError(String sequenceId, ComagentsError error) {
        gateway.completeWithError(sequenceId, toThrowable(error));
    }

    /**
     * The error as the exception it was on the sender's side: an instance of {@code errorClassName} with
     * {@code errorMessage}, when that is a {@link Throwable} known here with a {@code (String)} constructor; otherwise
     * an {@link IllegalStateException} naming the class.
     */
    static Throwable toThrowable(ComagentsError error) {
        return throwableClass(error.errorClassName())
                .flatMap(type -> instantiate(type, error.errorMessage()))
                .orElseGet(() -> new IllegalStateException(error.errorClassName() + ": " + error.errorMessage()));
    }

    // not initialized before it is known to be a Throwable: the name comes from another device
    private static Optional<Class<? extends Throwable>> throwableClass(String className) {
        if (className == null) {
            return Optional.empty();
        }
        try {
            Class<?> type = Class.forName(className, false, ComagentsGatewayResponseListener.class.getClassLoader());
            return Throwable.class.isAssignableFrom(type)
                    ? Optional.of(type.asSubclass(Throwable.class))
                    : Optional.empty();
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static Optional<Throwable> instantiate(Class<? extends Throwable> type, String message) {
        try {
            return Optional.of(type.getConstructor(String.class).newInstance(message));
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
