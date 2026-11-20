package com.comagents.agentorchestrator.gateway;

import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.request.AgentStatusRequest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ComagentsGatewayTest {

    private static final DeliveryAddress EXECUTOR =
            DeliveryAddress.agentExecutor("executor-device-1", AiHarnessProvider.CLAUDE_CODE);

    private final RecordingDelivery delivery = new RecordingDelivery();
    private final ComagentsGateway gateway = new ComagentsGateway(delivery, Duration.ofSeconds(5));

    @Test
    void returnsTheResponseWithTheRequestsSequenceId() {
        delivery.onDeliver = sent -> gateway.completeWithResponse(sent.sequenceId(), "pong");

        assertEquals("pong", gateway.registerRequest(EXECUTOR, "ping", String.class));
        assertEquals(EXECUTOR, delivery.sent.getFirst().address());
    }

    @Test
    void aRequestWithSequenceIdIsSentWithIt() {
        AgentStatusRequest request =
                new AgentStatusRequest(AiAgent.CODE_REVIEW, UUID.randomUUID(), "sequence-1", "orchestrator-device-1");
        delivery.onDeliver = sent -> gateway.completeWithResponse(sent.sequenceId(), "status");

        gateway.registerRequest(EXECUTOR, request, String.class);

        assertEquals("sequence-1", delivery.sent.getFirst().sequenceId());
    }

    @Test
    void aRequestWithBlankSequenceIdGetsANewOne() {
        delivery.onDeliver = sent -> gateway.completeWithResponse(sent.sequenceId(), "pong");

        gateway.registerRequest(EXECUTOR, new Ping(" "), String.class);
        gateway.registerRequest(EXECUTOR, new Ping(null), String.class);

        assertTrue(StringUtils.isNotBlank(delivery.sent.getFirst().sequenceId()));
        assertTrue(StringUtils.isNotBlank(delivery.sent.get(1).sequenceId()));
    }

    @Test
    void aRequestWithoutSequenceIdGetsANewOneEachTime() {
        delivery.onDeliver = sent -> gateway.completeWithResponse(sent.sequenceId(), "pong");

        gateway.registerRequest(EXECUTOR, "ping", String.class);
        gateway.registerRequest(EXECUTOR, "ping", String.class);

        assertNotNull(delivery.sent.getFirst().sequenceId());
        assertNotEquals(delivery.sent.getFirst().sequenceId(), delivery.sent.get(1).sequenceId());
    }

    @Test
    void rethrowsTheErrorReceivedInPlaceOfTheResponse() {
        IllegalStateException error = new IllegalStateException("agent failed");
        delivery.onDeliver = sent -> gateway.completeWithError(sent.sequenceId(), error);

        assertSame(error, assertThrows(IllegalStateException.class,
                () -> gateway.registerRequest(EXECUTOR, "ping", String.class)));
    }

    @Test
    void failsWhenNoResponseComesWithinTheTimeout() {
        ComagentsGateway impatient = new ComagentsGateway(delivery, Duration.ofMillis(200));

        IllegalStateException timedOut = assertThrows(IllegalStateException.class,
                () -> impatient.registerRequest(EXECUTOR, "ping", String.class));

        assertInstanceOf(TimeoutException.class, timedOut.getCause());
    }

    @Test
    void aLateResponseIsIgnored() {
        ComagentsGateway impatient = new ComagentsGateway(delivery, Duration.ofMillis(200));
        assertThrows(IllegalStateException.class, () -> impatient.registerRequest(EXECUTOR, "ping", String.class));

        // no one waits for it any more
        impatient.completeWithResponse(delivery.sent.getFirst().sequenceId(), "pong");
    }

    @Test
    void rethrowsWhatSendingFailedWith() {
        IllegalStateException notSent = new IllegalStateException("not sent");
        delivery.onDeliver = _ -> {
            throw notSent;
        };

        assertSame(notSent, assertThrows(IllegalStateException.class,
                () -> gateway.registerRequest(EXECUTOR, "ping", String.class)));
    }

    @Test
    void aResponseOfAnotherClassIsRejected() {
        delivery.onDeliver = sent -> gateway.completeWithResponse(sent.sequenceId(), 42);

        assertThrows(ClassCastException.class, () -> gateway.registerRequest(EXECUTOR, "ping", String.class));
    }

    @Test
    void aSequenceIdStillWaitingIsRejected() {
        AgentStatusRequest request =
                new AgentStatusRequest(AiAgent.CODE_REVIEW, UUID.randomUUID(), "sequence-1", "orchestrator-device-1");
        // the same request again while the first one waits: rejected before it is sent
        delivery.onDeliver = sent -> {
            assertThrows(IllegalArgumentException.class, () -> gateway.registerRequest(EXECUTOR, request, String.class));
            gateway.completeWithResponse(sent.sequenceId(), "status");
        };

        assertEquals("status", gateway.registerRequest(EXECUTOR, request, String.class));
    }

    private record Ping(String sequenceId) implements WithSequenceId {
    }

    private record Sent(DeliveryAddress address, String sequenceId, Object payload) {
    }

    private static final class RecordingDelivery implements ComagentsMessageDeliveryService {

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        // what the executor does with the request: answers it right away, by default never
        volatile Consumer<Sent> onDeliver = _ -> {
        };

        @Override
        public <T> void deliverToAddress(DeliveryAddress address, String sequenceId, T payload) {
            Sent message = new Sent(address, sequenceId, payload);
            sent.add(message);
            onDeliver.accept(message);
        }

        @Override
        public void deliverErrorToAddress(DeliveryAddress address, String sequenceId, Class<?> payloadClass,
                                          Throwable error) {
            throw new UnsupportedOperationException();
        }
    }
}
