package com.comagents.commons.communication;

import com.comagents.commons.serialization.JsonUtility;

public record ComagentsMessage<T>(Class<T> payloadClass, T payload) {

    private static final String PAYLOAD_STRING_DELIMITER = "::";
    private static final String PAYLOAD_STRING_TEMPLATE = "%s" + PAYLOAD_STRING_DELIMITER + "%s";

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return PAYLOAD_STRING_TEMPLATE.formatted(
                payloadClass.getName(),
                JsonUtility.toJson(payload)
        );
    }
}
