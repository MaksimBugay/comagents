package com.comagents.commons.communication;

import com.comagents.commons.serialization.JsonUtility;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.Validate;

/**
 * The payload of a {@link ComagentsMessage} and its class; its string form, {@code payloadClass::json}, is
 * the message's {@code payloadString}.
 */
public record ComagentsMessagePayload<T>(T payload, Class<T> payloadClass) {

    private static final String PAYLOAD_STRING_DELIMITER = "::";
    private static final String PAYLOAD_STRING_TEMPLATE = "%s" + PAYLOAD_STRING_DELIMITER + "%s";

    public ComagentsMessagePayload {
        Validate.notNull(payloadClass, "payloadClass must not be null");
        if (payload instanceof CharSequence text) {
            Validate.notBlank(text, "payload must not be blank");
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return PAYLOAD_STRING_TEMPLATE.formatted(
                payloadClass.getName(),
                JsonUtility.toJson(payload)
        );
    }

    static <P> ComagentsMessagePayload<P> fromPayloadString(String payloadString, Class<P> payloadClass) {
        String payloadClassName = payloadClassName(payloadString);
        if (!payloadClass.getName().equals(payloadClassName)) {
            throw new IllegalArgumentException("Payload class " + payloadClassName + " is not "
                    + payloadClass.getName());
        }
        String payloadJson = payloadString.substring(payloadClassName.length() + PAYLOAD_STRING_DELIMITER.length());
        return new ComagentsMessagePayload<>(JsonUtility.fromJson(payloadJson, payloadClass), payloadClass);
    }

    /**
     * The payload class name of a payload string, read without loading the class or parsing the payload.
     */
    static String payloadClassName(String payloadString) {
        if (StringUtils.isBlank(payloadString)) {
            throw new IllegalArgumentException("payloadString must not be blank");
        }
        return payloadString.substring(0, delimiterIndex(payloadString));
    }

    private static int delimiterIndex(String payloadString) {
        int delimiterIndex = Strings.CS.indexOf(payloadString, PAYLOAD_STRING_DELIMITER);
        if (delimiterIndex <= 0) {
            throw new IllegalArgumentException("Payload string is not in the 'payloadClass" + PAYLOAD_STRING_DELIMITER
                    + "payloadJson' format: " + payloadString);
        }
        return delimiterIndex;
    }
}
