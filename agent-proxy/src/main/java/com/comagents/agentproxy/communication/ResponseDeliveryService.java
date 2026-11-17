package com.comagents.agentproxy.communication;

public interface ResponseDeliveryService {

    <T> void deliverToOrchestrator(String sequenceId, String orchestratorDeviceId, T response);
}
