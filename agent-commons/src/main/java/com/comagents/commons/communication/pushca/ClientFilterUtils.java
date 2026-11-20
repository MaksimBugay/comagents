package com.comagents.commons.communication.pushca;

import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.commons.communication.DeliveryAddress;

/** The Pushca receivers of {@link DeliveryAddress}es. */
public final class ClientFilterUtils {

    public static final String COMAGENTS_WORKSPACE_ID = "comagents-main";

    private ClientFilterUtils() {
    }

    /** The clients of {@code address} in the comagents workspace; its null ids aren't filtered on. */
    public static ClientFilter of(DeliveryAddress address) {
        return new ClientFilter(COMAGENTS_WORKSPACE_ID, address.accountId(), address.deviceId(),
                address.applicationId());
    }
}
