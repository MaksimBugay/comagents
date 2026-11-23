package com.comagents.commons.communication;

import com.comagents.commons.domain.AiHarnessProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import static com.comagents.commons.communication.CommunicationUtils.AGENT_EXECUTOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_APPLICATION_ID;
import static com.comagents.commons.communication.CommunicationUtils.buildAgentExecutorApplicationId;

/**
 * Where a message goes, whatever the transport: the device {@code deviceId} of the account {@code accountId}, where
 * the application {@code applicationId} gets it. A blank one is kept as null and isn't filtered on, but one of them
 * must be given.
 */
public record DeliveryAddress(String accountId, String deviceId, String applicationId) {

    /**
     * @throws IllegalArgumentException when the account id, device id and application id all are blank
     */
    public DeliveryAddress {
        Validate.isTrue(!StringUtils.isAllBlank(accountId, deviceId, applicationId),
                "one of accountId, deviceId and applicationId must not be blank");
        accountId = nullIfBlank(accountId);
        deviceId = nullIfBlank(deviceId);
        applicationId = nullIfBlank(applicationId);
    }

    /**
     * The agent executor of {@code harnessProvider} on the device {@code deviceId}.
     *
     * @throws NullPointerException     when the device id or harness provider is null
     * @throws IllegalArgumentException when the device id is blank
     */
    public static DeliveryAddress agentExecutor(String deviceId, AiHarnessProvider harnessProvider) {
        Validate.notBlank(deviceId, "deviceId must not be blank");
        Validate.notNull(harnessProvider, "harnessProvider must not be null");
        return new DeliveryAddress(AGENT_EXECUTOR_ACCOUNT_ID, deviceId,
                buildAgentExecutorApplicationId(harnessProvider));
    }

    /**
     * The agent orchestrator on the device {@code deviceId}.
     *
     * @throws NullPointerException     when the device id is null
     * @throws IllegalArgumentException when the device id is blank
     */
    public static DeliveryAddress agentOrchestrator(String deviceId) {
        Validate.notBlank(deviceId, "deviceId must not be blank");
        return new DeliveryAddress(ORCHESTRATOR_ACCOUNT_ID, deviceId, ORCHESTRATOR_APPLICATION_ID);
    }

    /**
     * Every agent orchestrator, on whatever device.
     */
    public static DeliveryAddress agentOrchestrators() {
        return new DeliveryAddress(ORCHESTRATOR_ACCOUNT_ID, null, ORCHESTRATOR_APPLICATION_ID);
    }

    /**
     * The device {@code deviceId}, whatever its account and application: one device id is one agent-proxy or
     * orchestrator in the cluster.
     *
     * @throws NullPointerException     when the device id is null
     * @throws IllegalArgumentException when the device id is blank
     */
    public static DeliveryAddress deviceWithId(String deviceId) {
        Validate.notBlank(deviceId, "deviceId must not be blank");
        return new DeliveryAddress(null, deviceId, null);
    }

    private static String nullIfBlank(String id) {
        return StringUtils.defaultIfBlank(id, null);
    }
}
