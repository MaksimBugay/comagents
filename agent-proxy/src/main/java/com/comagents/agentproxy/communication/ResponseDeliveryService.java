package com.comagents.agentproxy.communication;

public interface ResponseDeliveryService {

    <T> void deliverToOrchestrator(String orchestratorDeviceId, T response);
}
