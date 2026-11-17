package com.comagents.commons.communication;

import com.comagents.commons.serialization.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComagentsMessagePayloadTest {

    record Task(String title, List<String> steps, Integer priority) {
    }

    @Test
    void roundTripKeepsPayloadClassAndPayload() {
        ComagentsMessagePayload<Task> payload =
                new ComagentsMessagePayload<>(new Task("review :: PR", List.of("clone"), null), Task.class);

        assertEquals(payload, ComagentsMessagePayload.fromPayloadString(payload.toString(), Task.class));
    }

    @Test
    void payloadClassNameIsReadWithoutLoadingTheClass() {
        assertEquals("com.comagents.elsewhere.Missing",
                ComagentsMessagePayload.payloadClassName("com.comagents.elsewhere.Missing::not parsed"));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessagePayload.payloadClassName("no delimiter"));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessagePayload.payloadClassName("::{}"));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessagePayload.payloadClassName(" "));
    }

    @Test
    void payloadClassMustMatchThePayloadString() {
        String payloadString = new ComagentsMessagePayload<>("text", String.class).toString();

        assertThrows(IllegalArgumentException.class,
                () -> ComagentsMessagePayload.fromPayloadString(payloadString, Task.class));
    }

    @Test
    void blankTextPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessagePayload<>("", String.class));
        assertThrows(IllegalArgumentException.class,
                () -> new ComagentsMessagePayload<>(new StringBuilder(" "), StringBuilder.class));
    }

    @Test
    void payloadNotMatchingItsClassIsRejected() {
        String payloadString = Task.class.getName() + "::[1,2]";

        assertThrows(SerializationException.class,
                () -> ComagentsMessagePayload.fromPayloadString(payloadString, Task.class));
    }
}
