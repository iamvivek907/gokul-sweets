package com.gokulsweets.restaurant.order.service.lifecycle.config;

import com.gokulsweets.restaurant.order.lifecycle.config.PickupLifecycleProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickupLifecyclePropertiesTest {

    @Test
    void defaultNoShowWindowIsAfterExpiryGrace() {
        PickupLifecycleProperties properties = new PickupLifecycleProperties();

        assertTrue(properties.isNoShowAfterExpiry());
        assertFalse(properties.isAutomaticExpiryEnabled());
    }

    @Test
    void rejectsNoShowWindowBeforeExpiryGrace() {
        PickupLifecycleProperties properties = new PickupLifecycleProperties();
        properties.setPickupExpiryGraceMinutes(60);
        properties.setNoShowAfterMinutes(30);

        assertFalse(properties.isNoShowAfterExpiry());
    }
}
