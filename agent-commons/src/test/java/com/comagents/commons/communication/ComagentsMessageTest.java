package com.comagents.commons.communication;

import com.comagents.commons.converter.Base64Converter;
import com.comagents.commons.serialization.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComagentsMessageTest {

    record Task(String title, List<String> steps, Integer priority) {
    }

    private static final Task TASK = new Task("review :: PR", List.of("clone", "review"), null);
    private static final String TASK_CLASS = Task.class.getName();
    private static final String ID = "message-1";
    private static final String ID_JSON = "\"id\":\"" + ID + "\",";
    private static final String ERROR_JSON =
            "{\"code\":0,\"errorMessage\":\"x\",\"errorClassName\":\"com.comagents.elsewhere.AgentFailed\"}";

    @Test
    void roundTripKeepsPayloadClassNameAndPayload() {
        ComagentsMessage message = ComagentsMessage.ofPayload(ID, TASK, Task.class);

        ComagentsMessage parsed = ComagentsMessage.fromComagentsMessageString(message.toString());

        assertEquals(message, parsed);
        assertEquals(message.id(), parsed.id());
        assertEquals(TASK_CLASS, parsed.payloadClassName());
        assertEquals(TASK, parsed.comagentsMessagePayload().payload());
        assertNull(parsed.error());
    }

    @Test
    void roundTripOfStringPayload() {
        ComagentsMessage message = ComagentsMessage.ofPayload(ID, "multi\nline ✓", String.class);

        assertEquals("multi\nline ✓", ComagentsMessage.fromComagentsMessageString(message.toString())
                .comagentsMessagePayload().payload());
    }

    @Test
    void roundTripOfErrorKeepsPayloadClassName() {
        ComagentsMessage message = ComagentsMessage.ofError(ID, Task.class, new IllegalStateException("agent failed"));

        ComagentsMessage parsed = ComagentsMessage.fromComagentsMessageString(message.toString());

        assertEquals(message, parsed);
        assertEquals(TASK_CLASS, parsed.payloadClassName());
        assertEquals(new ComagentsError(0, "agent failed", IllegalStateException.class.getName()), parsed.error());
        assertNull(parsed.payloadString());
    }

    @Test
    void messageWithoutIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.ofPayload(" ", TASK, Task.class));
        assertThrows(NullPointerException.class,
                () -> ComagentsMessage.ofError(null, Task.class, new IllegalStateException("agent failed")));
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessage(" ", TASK_CLASS + "::{}", TASK_CLASS,
                null));
        assertThrows(SerializationException.class, () -> ComagentsMessage.fromComagentsMessageString(
                message("{\"payloadString\":\"" + TASK_CLASS + "::{}\",\"payloadClassName\":\"" + TASK_CLASS
                        + "\"}")));
    }

    @Test
    void messageOfClassesUnknownHereIsReadWithoutLoadingThem() {
        ComagentsMessage parsed = ComagentsMessage.fromComagentsMessageString(message(
                "{" + ID_JSON + "\"payloadClassName\":\"com.comagents.elsewhere.Missing\",\"error\":" + ERROR_JSON + "}"));

        assertEquals("com.comagents.elsewhere.Missing", parsed.payloadClassName());
        assertEquals("com.comagents.elsewhere.AgentFailed", parsed.error().errorClassName());
    }

    @Test
    void payloadOfClassUnknownHereIsRejected() {
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(message(
                "{" + ID_JSON + "\"payloadString\":\"com.comagents.elsewhere.Missing::{}\","
                        + "\"payloadClassName\":\"com.comagents.elsewhere.Missing\"}"));

        assertThrows(IllegalArgumentException.class, message::comagentsMessagePayload);
    }

    @Test
    void exactlyOneOfPayloadAndErrorIsSet() {
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.ofPayload(ID, null, Task.class));
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.ofError(ID, Task.class, null));
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessage(ID, TASK_CLASS + "::{}", TASK_CLASS,
                new ComagentsError(new IllegalStateException("agent failed"))));
    }

    @Test
    void messageReadWithNeitherOrBothOfPayloadAndErrorIsRejected() {
        // Jackson wraps the constructor's exception
        assertThrows(SerializationException.class, () -> ComagentsMessage.fromComagentsMessageString(
                message("{" + ID_JSON + "\"payloadClassName\":\"" + TASK_CLASS + "\"}")));
        assertThrows(SerializationException.class, () -> ComagentsMessage.fromComagentsMessageString(
                message("{" + ID_JSON + "\"payloadString\":\"" + TASK_CLASS + "::{}\",\"payloadClassName\":\""
                        + TASK_CLASS + "\",\"error\":" + ERROR_JSON + "}")));
    }

    @Test
    void messageWithoutPayloadClassNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessage(ID, null, " ", new ComagentsError(
                new IllegalStateException("agent failed"))));
        assertThrows(SerializationException.class, () -> ComagentsMessage.fromComagentsMessageString(
                message("{" + ID_JSON + "\"error\":" + ERROR_JSON + "}")));
    }

    @Test
    void payloadStringMustBeOfThePayloadClass() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComagentsMessage(ID, "java.lang.String::\"text\"", TASK_CLASS, null));
        assertThrows(SerializationException.class, () -> ComagentsMessage.fromComagentsMessageString(
                message("{" + ID_JSON + "\"payloadString\":\"java.lang.String::\\\"text\\\"\",\"payloadClassName\":\"" + TASK_CLASS
                        + "\"}")));
    }

    @Test
    void nullPayloadClassIsRejected() {
        assertThrows(NullPointerException.class, () -> ComagentsMessage.ofPayload(ID, TASK, null));
        assertThrows(NullPointerException.class, () -> ComagentsMessage.ofError(ID, null, new IllegalStateException()));
    }

    @Test
    void blankTextPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.ofPayload(ID, " \n", String.class));
    }

    @Test
    void blankMessageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ComagentsMessage.fromComagentsMessageString(" "));
    }

    @Test
    void messageThatIsNotJsonIsRejected() {
        assertThrows(SerializationException.class,
                () -> ComagentsMessage.fromComagentsMessageString(message("not json")));
    }

    private static String message(String json) {
        return Base64Converter.toBase64String(json);
    }
}
