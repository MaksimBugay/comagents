package com.comagents.commons.communication;

import com.comagents.commons.serialization.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComagentsMessageTest {

    record Task(String title, List<String> steps, Integer priority) {
    }

    @Test
    void roundTripKeepsPayloadClassAndPayload() {
        ComagentsMessage<Task> message = new ComagentsMessage<>(Task.class,
                new Task("review :: PR", List.of("clone", "review"), null));

        assertEquals(message, ComagentsMessage.fromMessageString(message.toString()));
    }

    @Test
    void roundTripOfStringPayload() {
        ComagentsMessage<String> message = new ComagentsMessage<>(String.class, "multi\nline ✓");

        assertEquals(message, ComagentsMessage.fromMessageString(message.toString()));
    }

    @Test
    void roundTripWithKnownPayloadClass() {
        ComagentsMessage<Task> message = new ComagentsMessage<>(Task.class, new Task("review", List.of(), 1));

        ComagentsMessage<Task> parsed = ComagentsMessage.fromMessageString(message.toString(), Task.class);

        assertEquals(message, parsed);
    }

    @Test
    void payloadClassNameIsReadWithoutLoadingTheClass() {
        assertEquals("com.comagents.elsewhere.Missing",
                ComagentsMessage.payloadClassName("com.comagents.elsewhere.Missing::bm90IGRlY29kZWQ="));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.payloadClassName("no delimiter"));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.payloadClassName(" "));
    }

    @Test
    void knownPayloadClassMustMatchTheMessage() {
        String message = new ComagentsMessage<>(String.class, "text").toString();

        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.fromMessageString(message, Task.class));
        assertThrows(NullPointerException.class, () -> ComagentsMessage.fromMessageString(message, null));
    }

    @Test
    void nullPayloadClassIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ComagentsMessage<>(null, new Task("review", List.of(), 1)));
    }

    @Test
    void nullPayloadIsRejected() {
        assertThrows(NullPointerException.class, () -> new ComagentsMessage<>(Task.class, null));
        // JSON null decoded from "bnVsbA=="
        assertThrows(NullPointerException.class,
                () -> ComagentsMessage.fromMessageString(Task.class.getName() + "::bnVsbA=="));
    }

    @Test
    void blankTextPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessage<>(String.class, ""));
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessage<>(String.class, " \n"));
        assertThrows(IllegalArgumentException.class,
                () -> new ComagentsMessage<>(StringBuilder.class, new StringBuilder(" ")));
    }

    @Test
    void messageWithoutPayloadClassIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.fromMessageString("no delimiter"));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.fromMessageString("::bnVsbA=="));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.fromMessageString(null));
    }

    @Test
    void unknownPayloadClassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComagentsMessage.fromMessageString("com.comagents.Missing::bnVsbA=="));
    }

    @Test
    void payloadNotMatchingItsClassIsRejected() {
        String message = Task.class.getName() + "::" + "WzEsMl0="; // [1,2]

        assertThrows(SerializationException.class, () -> ComagentsMessage.fromMessageString(message));
    }
}
