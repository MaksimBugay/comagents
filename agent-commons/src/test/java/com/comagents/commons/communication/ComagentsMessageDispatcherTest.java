package com.comagents.commons.communication;

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

        List<T> payloads() {
            return payloads;
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

        dispatcher.dispatch(new ComagentsMessage<>(Task.class, task).toString());

        assertEquals(List.of(task), taskListener.payloads());
        assertEquals(List.of(task), secondTaskListener.payloads());
        assertTrue(noteListener.payloads().isEmpty());
    }

    @Test
    void messagesAreRoutedByPayloadClass() {
        Task task = new Task("review", 1);
        Note first = new Note("first");
        Note second = new Note("second");

        dispatcher.dispatch(new ComagentsMessage<>(Note.class, first).toString());
        dispatcher.dispatch(new ComagentsMessage<>(Task.class, task).toString());
        dispatcher.dispatch(new ComagentsMessage<>(Note.class, second).toString());

        assertEquals(List.of(first, second), noteListener.payloads());
        assertEquals(List.of(task), taskListener.payloads());
    }

    @Test
    void messageWithoutListenersIsIgnored() {
        assertDoesNotThrow(() -> dispatcher.dispatch(new ComagentsMessage<>(String.class, "nobody").toString()));

        assertTrue(taskListener.payloads().isEmpty());
        assertTrue(noteListener.payloads().isEmpty());
    }

    @Test
    void messageOfClassUnknownHereIsIgnored() {
        assertDoesNotThrow(() -> dispatcher.dispatch("com.comagents.elsewhere.Missing::eyJhIjoxfQ=="));

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
                () -> failingDispatcher.dispatch(new ComagentsMessage<>(Task.class, task).toString()));

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

        assertDoesNotThrow(() -> empty.dispatch(new ComagentsMessage<>(Task.class, new Task("t", 1)).toString()));
    }

    @Test
    void nullListenersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComagentsMessageDispatcher(null));
    }

    @Test
    void malformedMessageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("no delimiter"));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(" "));
        assertTrue(taskListener.payloads().isEmpty());
    }
}
