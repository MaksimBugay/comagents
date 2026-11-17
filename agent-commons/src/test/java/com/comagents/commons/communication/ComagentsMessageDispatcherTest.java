package com.comagents.commons.communication;

import com.comagents.commons.converter.Base64Converter;
import com.comagents.commons.serialization.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComagentsMessageDispatcherTest {

    record Task(String title, Integer priority) {
    }

    record Note(String text) {
    }

    static class RecordingListener<T> implements ComagentsMessageListener<T> {

        private final Class<T> payloadClass;
        private final List<T> payloads = new ArrayList<>();
        private final List<ComagentsError> errors = new ArrayList<>();

        RecordingListener(Class<T> payloadClass) {
            this.payloadClass = payloadClass;
        }

        @Override
        public Class<T> getPayloadClass() {
            return payloadClass;
        }

        @Override
        public void consumePayload(T payload) {
            payloads.add(payload);
        }

        @Override
        public void consumeError(ComagentsError error) {
            errors.add(error);
        }

        List<T> payloads() {
            return payloads;
        }

        List<ComagentsError> errors() {
            return errors;
        }
    }

    // only the required methods: errors are ignored
    static class PayloadOnlyListener implements ComagentsMessageListener<Task> {

        @Override
        public Class<Task> getPayloadClass() {
            return Task.class;
        }

        @Override
        public void consumePayload(Task payload) {
            throw new AssertionError("an error message has no payload");
        }
    }

    static class FailingListener<T> extends RecordingListener<T> {

        private final String error;

        FailingListener(Class<T> payloadClass, String error) {
            super(payloadClass);
            this.error = error;
        }

        @Override
        public void consumePayload(T payload) {
            super.consumePayload(payload);
            throw new IllegalStateException(error);
        }
    }

    private final RecordingListener<Task> taskListener = new RecordingListener<>(Task.class);
    private final RecordingListener<Task> secondTaskListener = new RecordingListener<>(Task.class);
    private final RecordingListener<Note> noteListener = new RecordingListener<>(Note.class);
    private final ComagentsMessageDispatcher dispatcher =
            new ComagentsMessageDispatcher(List.of(taskListener, secondTaskListener, noteListener));

    @Test
    void payloadGoesToEveryListenerOfItsClass() {
        Task task = new Task("review", 1);

        dispatcher.dispatch(ComagentsMessage.ofPayload(task, Task.class).toString());

        assertEquals(List.of(task), taskListener.payloads());
        assertEquals(List.of(task), secondTaskListener.payloads());
        assertTrue(noteListener.payloads().isEmpty());
    }

    @Test
    void errorGoesToEveryListenerOfItsClassInPlaceOfThePayload() {
        ComagentsError error = new ComagentsError(new IllegalStateException("agent failed"));

        dispatcher.dispatch(ComagentsMessage.ofError(Task.class, new IllegalStateException("agent failed")).toString());

        assertEquals(List.of(error), taskListener.errors());
        assertEquals(List.of(error), secondTaskListener.errors());
        assertTrue(taskListener.payloads().isEmpty());
        assertTrue(noteListener.errors().isEmpty());
    }

    @Test
    void errorIsIgnoredByListenerThatDoesNotHandleErrors() {
        ComagentsMessageDispatcher payloadOnly = new ComagentsMessageDispatcher(List.of(new PayloadOnlyListener()));
        String message = ComagentsMessage.ofError(Task.class, new IllegalStateException("x")).toString();

        assertDoesNotThrow(() -> payloadOnly.dispatch(message));
    }

    @Test
    void messagesAreRoutedByPayloadClass() {
        Task task = new Task("review", 1);
        Note first = new Note("first");
        Note second = new Note("second");

        dispatcher.dispatch(ComagentsMessage.ofPayload(first, Note.class).toString());
        dispatcher.dispatch(ComagentsMessage.ofPayload(task, Task.class).toString());
        dispatcher.dispatch(ComagentsMessage.ofPayload(second, Note.class).toString());

        assertEquals(List.of(first, second), noteListener.payloads());
        assertEquals(List.of(task), taskListener.payloads());
    }

    @Test
    void messageWithoutListenersIsIgnored() {
        assertDoesNotThrow(() -> dispatcher.dispatch(ComagentsMessage.ofPayload("nobody", String.class).toString()));

        assertTrue(taskListener.payloads().isEmpty());
        assertTrue(noteListener.payloads().isEmpty());
    }

    @Test
    void messageOfClassUnknownHereIsIgnored() {
        String message = Base64Converter.toBase64String("{\"payloadString\":\"com.comagents.elsewhere.Missing::{}\","
                + "\"payloadClassName\":\"com.comagents.elsewhere.Missing\"}");

        assertDoesNotThrow(() -> dispatcher.dispatch(message));
        assertTrue(taskListener.payloads().isEmpty());
        assertTrue(noteListener.payloads().isEmpty());
    }

    @Test
    void failingListenerDoesNotStopTheOthers() {
        FailingListener<Task> firstFailing = new FailingListener<>(Task.class, "first");
        FailingListener<Task> secondFailing = new FailingListener<>(Task.class, "second");
        ComagentsMessageDispatcher failingDispatcher =
                new ComagentsMessageDispatcher(List.of(firstFailing, taskListener, secondFailing, secondTaskListener));
        Task task = new Task("review", 1);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> failingDispatcher.dispatch(ComagentsMessage.ofPayload(task, Task.class).toString()));

        assertEquals(List.of(task), firstFailing.payloads());
        assertEquals(List.of(task), taskListener.payloads());
        assertEquals(List.of(task), secondFailing.payloads());
        assertEquals(List.of(task), secondTaskListener.payloads());
        assertEquals("first", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("second", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void dispatcherWithoutListenersIgnoresMessages() {
        ComagentsMessageDispatcher empty = new ComagentsMessageDispatcher(List.of());

        assertDoesNotThrow(() -> empty.dispatch(ComagentsMessage.ofPayload(new Task("t", 1), Task.class).toString()));
    }

    @Test
    void nullListenersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessageDispatcher(null));
    }

    @Test
    void malformedMessageIsRejected() {
        assertThrows(SerializationException.class,
                () -> dispatcher.dispatch(Base64Converter.toBase64String("not json")));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(" "));
        assertThrows(SerializationException.class,
                () -> dispatcher.dispatch(Base64Converter.toBase64String("{\"payloadString\":\"x::{}\"}")));
        assertTrue(taskListener.payloads().isEmpty());
    }
}
