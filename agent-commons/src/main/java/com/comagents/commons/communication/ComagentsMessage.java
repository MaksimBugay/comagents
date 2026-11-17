package com.comagents.commons.communication;

import com.comagents.commons.converter.Base64Converter;
import com.comagents.commons.serialization.JsonUtility;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.Validate;

public record ComagentsMessage<T>(Class<T> payloadClass, T payload) {

    private static final String PAYLOAD_STRING_DELIMITER = "::";
    private static final String PAYLOAD_STRING_TEMPLATE = "%s" + PAYLOAD_STRING_DELIMITER + "%s";

    public ComagentsMessage {
        Validate.notNull(payloadClass, "payloadClass must not be null");
        validatePayload(payload);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return PAYLOAD_STRING_TEMPLATE.formatted(
                payloadClass.getName(),
                Base64Converter.toBase64String(JsonUtility.toJson(payload))
        );
    }

    public static ComagentsMessage<?> fromMessageString(String message) {
        return fromMessageString(message, payloadClass(payloadClassName(message)));
    }

    /**
     * Parses a message whose payload class the caller already knows, without loading any class by name.
     */
    public static <P> ComagentsMessage<P> fromMessageString(String message, Class<P> payloadClass) {
        Validate.notNull(payloadClass, "payloadClass must not be null");
        String payloadClassName = payloadClassName(message);
        if (!payloadClass.getName().equals(payloadClassName)) {
            throw new IllegalArgumentException("Message payload class " + payloadClassName + " is not "
                    + payloadClass.getName());
        }
        String payloadJson = Base64Converter.fromBase64String(
                message.substring(payloadClassName.length() + PAYLOAD_STRING_DELIMITER.length()));
        return new ComagentsMessage<>(payloadClass, JsonUtility.fromJson(payloadJson, payloadClass));
    }

    /**
     * The payload class name of a message, read without loading the class or decoding the payload.
     */
    public static String payloadClassName(String message) {
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message.substring(0, delimiterIndex(message));
    }

    private static void validatePayload(Object payload) {
        Validate.notNull(payload, "payload must not be null");
        if (payload instanceof CharSequence text) {
            Validate.notBlank(text, "payload must not be blank");
        }
    }

    private static int delimiterIndex(String message) {
        int delimiterIndex = Strings.CS.indexOf(message, PAYLOAD_STRING_DELIMITER);
        if (delimiterIndex <= 0) {
            throw new IllegalArgumentException("Message is not in the 'payloadClass" + PAYLOAD_STRING_DELIMITER
                    + "base64Payload' format: " + message);
        }
        return delimiterIndex;
    }

    private static Class<?> payloadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown payload class: " + className, e);
        }
    }
}
