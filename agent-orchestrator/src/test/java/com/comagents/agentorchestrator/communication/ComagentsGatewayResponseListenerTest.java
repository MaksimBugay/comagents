package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.ComagentsError;
import com.comagents.commons.communication.WithSequenceId;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ComagentsGatewayResponseListenerTest {

    private final ComagentsGateway gateway = mock(ComagentsGateway.class);
    private final PongListener listener = new PongListener(gateway);

    @Test
    void passesTheResponseToTheGateway() {
        Pong pong = new Pong("sequence-1");

        listener.consumePayload("sequence-1", pong);

        verify(gateway).completeWithResponse("sequence-1", pong);
    }

    @Test
    void recreatesTheSendersException() {
        Throwable error = receivedError(new ComagentsError(new UnsupportedOperationException("not here")));

        assertInstanceOf(UnsupportedOperationException.class, error);
        assertEquals("not here", error.getMessage());
    }

    @Test
    void recreatesACheckedException() {
        Throwable error = receivedError(new ComagentsError(new TimeoutException("too late")));

        assertInstanceOf(TimeoutException.class, error);
        assertEquals("too late", error.getMessage());
    }

    @Test
    void recreatesAnExecutorsSessionLimitException() {
        Throwable error = receivedError(new ComagentsError(new ClaudeSessionLimitException(3)));

        assertInstanceOf(ClaudeSessionLimitException.class, error);
        assertEquals("All 3 Claude Code session slots are busy, try again later", error.getMessage());
    }

    @Test
    void anExceptionUnknownHereBecomesAnIllegalStateException() {
        Throwable error = receivedError(new ComagentsError(0, "session in use",
                "com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException"));

        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException: session in use",
                error.getMessage());
    }

    @Test
    void aClassThatIsNoThrowableIsNotInstantiated() {
        Throwable error = receivedError(new ComagentsError(0, "boom", String.class.getName()));

        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("java.lang.String: boom", error.getMessage());
    }

    @Test
    void anExceptionWithoutStringConstructorBecomesAnIllegalStateException() {
        Throwable error = receivedError(new ComagentsError(0, "boom", WithoutMessage.class.getName()));

        assertInstanceOf(IllegalStateException.class, error);
    }

    private Throwable receivedError(ComagentsError error) {
        listener.consumeError("sequence-1", error);
        ArgumentCaptor<Throwable> passed = ArgumentCaptor.forClass(Throwable.class);
        verify(gateway).completeWithError(eq("sequence-1"), passed.capture());
        return passed.getValue();
    }

    private record Pong(String sequenceId) implements WithSequenceId {
    }

    static final class WithoutMessage extends RuntimeException {

        WithoutMessage(int code) {
            super(String.valueOf(code));
        }
    }

    private static final class PongListener extends ComagentsGatewayResponseListener<Pong> {

        PongListener(ComagentsGateway gateway) {
            super(gateway);
        }

        @Override
        public Class<Pong> getPayloadClass() {
            return Pong.class;
        }
    }
}
