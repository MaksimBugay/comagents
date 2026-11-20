package com.comagents.commons.communication.pushca;

import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.commons.communication.DeliveryAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientFilterUtilsTest {

    @Test
    void theClientsOfAnAddressInTheComagentsWorkspace() {
        assertEquals(new ClientFilter("comagents-main", "someone@comagents.com", "device-1", "CHAT_ROOM"),
                ClientFilterUtils.of(new DeliveryAddress("someone@comagents.com", "device-1", "CHAT_ROOM")));
    }

    @Test
    void missingIdsAreNotFilteredOn() {
        assertEquals(new ClientFilter("comagents-main", null, "device-1", null),
                ClientFilterUtils.of(new DeliveryAddress(" ", "device-1", "")));
    }
}
