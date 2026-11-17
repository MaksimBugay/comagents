package com.comagents.commons.communication;

import com.comagents.commons.converter.Base64Converter;
import com.comagents.commons.serialization.JsonUtility;
import org.apache.commons.lang3.Validate;

import java.util.Objects;

/**
 * A message with the {@code id} its sender gives it: either the {@link ComagentsMessagePayload} string or the error
 * that took its place, never both, for the payload class named by {@code payloadClassName}. Class names are kept as
 * names, so a message is read without loading any class; the payload is read only by
 * {@link #comagentsMessagePayload()}.
 */
public record ComagentsMessage(String id, String payloadString, String payloadClassName, ComagentsError error) {

    public ComagentsMessage {
        Validate.notBlank(id, "id must not be blank");
        Validate.notBlank(payloadClassName, "payloadClassName must not be blank");
        Validate.isTrue((payloadString == null) != (error == null), "exactly one of payload and error must be set");
        if (payloadString != null) {
            String payloadStringClassName = ComagentsMessagePayload.payloadClassName(payloadString);
            Validate.isTrue(payloadClassName.equals(payloadStringClassName), "payload of %s in a message of %s",
                    payloadStringClassName, payloadClassName);
        }
    }

    private <P> ComagentsMessage(String id, P payload, Class<P> payloadClass, Throwable error) {
        Validate.notNull(payloadClass, "payloadClass must not be null");
        this(
                id,
                Objects.nonNull(payload) ? (new ComagentsMessagePayload<>(payload, payloadClass)).toString() : null,
                payloadClass.getName(),
                Objects.nonNull(error) ? new ComagentsError(error) : null
        );
    }

    public static <P> ComagentsMessage ofPayload(String id, P payload, Class<P> payloadClass) {
        return new ComagentsMessage(id, payload, payloadClass, null);
    }

    public static ComagentsMessage ofError(String id, Class<?> payloadClass, Throwable error) {
        return new ComagentsMessage(id, null, payloadClass, error);
    }

    /**
     * Reads the payload as the class {@code payloadClassName} names, which is loaded only now.
     *
     * @throws IllegalArgumentException when that class is unknown here
     */
    public ComagentsMessagePayload<?> comagentsMessagePayload() {
        return ComagentsMessagePayload.fromPayloadString(payloadString, payloadClass());
    }

    private Class<?> payloadClass() {
        try {
            return Class.forName(payloadClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown payload class: " + payloadClassName, e);
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return Base64Converter.toBase64String(JsonUtility.toJson(this));
    }

    public static ComagentsMessage fromComagentsMessageString(String comagentsMessageString) {
        Validate.notBlank(comagentsMessageString, "Comagents message must not be blank");
        return JsonUtility.fromJson(Base64Converter.fromBase64String(comagentsMessageString), ComagentsMessage.class);
    }
}
