package com.comagents.agentorchestrator;

import com.comagents.agentorchestrator.communication.pushca.FakePushcaConnectionFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_APPLICATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class ApplicationTest {

    @Inject
    FakePushcaConnectionFactory connectionFactory;

    @Test
    void opensThePushcaConnectionOnStartup() {
        assertEquals(1, connectionFactory.connections.size());
        assertEquals(ORCHESTRATOR_APPLICATION_ID, connectionFactory.connections.getFirst().client.applicationId);
    }
}
