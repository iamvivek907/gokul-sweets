package com.gokulsweets.restaurant.order.service;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.menu.MenuPickupDiscoveryService;
import com.gokulsweets.restaurant.order.controller.CartAvailabilityController;
import com.gokulsweets.restaurant.order.controller.VersionedMenuPreviewController;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

class VersionedMenuPreviewRoutingTest {
    @Test
    void springChoosesOptInRouteWhileCartAndLegacyRoutesRemainFresh() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            var features = new EnhancementProperties();
            features.setSmartAvailability(true);
            var service = mock(CartAvailabilityService.class);
            var legacy = mock(MenuPreviewReads.class);
            var previews = mock(VersionedMenuPreview.class);
            var discovery = mock(MenuPickupDiscoveryService.class);
            var date = LocalDate.of(2026, 10, 22);
            var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
            when(service.check(anyLong(), eq(date), eq(1), anyList(), eq(false)))
                    .thenReturn(result);
            when(legacy.check(anyLong(), eq(date), anyList())).thenReturn(result);
            when(previews.check(anyLong(), eq(date), anyList())).thenReturn(result);
            var mvc =
                    MockMvcBuilders.standaloneSetup(
                                    new CartAvailabilityController(
                                            features, service, discovery, legacy),
                                    new VersionedMenuPreviewController(
                                            features, legacy, previews, enabled))
                            .build();
            String path = "/api/branches/1/availability";
            String body =
                    "{\"startDate\":\"2026-10-22\",\"days\":1,\"fulfilmentType\":\"PICKUP\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
            mvc.perform(
                            post(path + "?menuPreview=true&compact=true&versioned=true")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
            if (enabled) verify(previews).check(eq(1L), eq(date), anyList());
            else {
                verify(legacy).check(eq(1L), eq(date), anyList());
                verifyNoInteractions(previews);
            }
            clearInvocations(legacy, previews);
            mvc.perform(
                            post(path + "?menuPreview=true&compact=true")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isOk());
            verify(legacy).check(eq(1L), eq(date), anyList());
            verifyNoInteractions(previews);
            mvc.perform(post(path).contentType("application/json").content(body))
                    .andExpect(status().isOk());
            verify(service).check(eq(1L), eq(date), eq(1), anyList(), eq(false));
            mvc.perform(get("/api/branches/1/menu-state?date=2026-10-22"))
                    .andExpect(enabled ? status().isOk() : status().isNotFound());
        }
    }
}
