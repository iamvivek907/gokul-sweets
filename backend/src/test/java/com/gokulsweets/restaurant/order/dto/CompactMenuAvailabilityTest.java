package com.gokulsweets.restaurant.order.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.menu.MenuPickupDiscoveryService;
import com.gokulsweets.restaurant.order.controller.CartAvailabilityController;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class CompactMenuAvailabilityTest {
    private Availability preview() {
        var date = LocalDate.of(2026, 10, 10);
        var issue =
                new ItemAvailability(
                        1L,
                        "Sweet",
                        "PIECE",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        false,
                        "NOT_READY",
                        "Ready after noon",
                        date.atTime(12, 0));
        var other =
                new ItemAvailability(
                        1L,
                        "Sweet",
                        "PIECE",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        false,
                        "SERVICE_WINDOW",
                        "Closed at this time",
                        null);
        return new Availability(
                "PICKUP",
                date,
                date.plusDays(10),
                List.of(
                        new DateAvailability(
                                date,
                                false,
                                List.of(
                                        new SlotAvailability(
                                                null,
                                                false,
                                                false,
                                                "First",
                                                "NOT_READY",
                                                List.of(issue, issue)),
                                        new SlotAvailability(
                                                null,
                                                false,
                                                false,
                                                "Second",
                                                "SERVICE_WINDOW",
                                                List.of(other)),
                                        new SlotAvailability(
                                                null, true, true, null, null, List.of())),
                                List.of(issue),
                                "Date reason",
                                true),
                        new DateAvailability(
                                date.plusDays(1),
                                false,
                                List.of(
                                        new SlotAvailability(
                                                null,
                                                false,
                                                false,
                                                "Next",
                                                "NOT_READY",
                                                List.of(issue))),
                                List.of(other),
                                null,
                                false)));
    }

    @Test
    void exactIssuesAndDecisionsRoundTripAcrossSlotsAndDates() {
        var source = preview();
        var encoded = CompactMenuAvailability.from(source);
        assertThat(encoded.issueCatalog()).hasSize(2);
        var dates =
                encoded.dates().stream()
                        .map(
                                date ->
                                        new DateAvailability(
                                                date.date(),
                                                date.available(),
                                                date.slots().stream()
                                                        .map(
                                                                slot ->
                                                                        new SlotAvailability(
                                                                                slot.slot(),
                                                                                slot
                                                                                        .normalAvailable(),
                                                                                slot
                                                                                        .priorityAvailable(),
                                                                                slot.reason(),
                                                                                slot.code(),
                                                                                slot
                                                                                        .issueIndexes()
                                                                                        .stream()
                                                                                        .map(
                                                                                                encoded
                                                                                                                .issueCatalog()
                                                                                                        ::get)
                                                                                        .toList()))
                                                        .toList(),
                                                date.items(),
                                                date.reason(),
                                                date.plannedProduction()))
                        .toList();
        assertThat(
                        new Availability(
                                encoded.fulfilmentType(),
                                encoded.today(),
                                encoded.maximumDate(),
                                dates))
                .isEqualTo(source);
    }

    @Test
    void indexingSubstantiallyReducesRepeatedUnavailableItemPayload() {
        var source = preview();
        var issues = new ArrayList<ItemAvailability>();
        for (long id = 1; id <= 100; id++)
            issues.add(
                    new ItemAvailability(
                            id,
                            "Unavailable sweet " + id,
                            "PIECE",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            false,
                            "NOT_READY",
                            "Daily stock is not ready",
                            null));
        var slots = new ArrayList<SlotAvailability>();
        for (int i = 0; i < 40; i++)
            slots.add(new SlotAvailability(null, false, false, "Not ready", "NOT_READY", issues));
        var full =
                new Availability(
                        "PICKUP",
                        source.today(),
                        source.maximumDate(),
                        List.of(
                                new DateAvailability(
                                        source.today(), false, slots, issues, "Not ready", false)));
        var mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        int oldBytes = mapper.writeValueAsBytes(full).length;
        int newBytes = mapper.writeValueAsBytes(CompactMenuAvailability.from(full)).length;
        System.out.printf(
                "Repeated-issue menu preview bytes: full=%d, compact=%d%n", oldBytes, newBytes);
        assertThat(newBytes).isLessThan(oldBytes / 5);
    }

    @Test
    void compactEncodingIsOptInAndNeverReplacesTheCartContract() {
        var features = new EnhancementProperties();
        features.setSmartAvailability(true);
        var service = mock(CartAvailabilityService.class);
        var query = mock(com.gokulsweets.restaurant.order.service.MenuPreviewQuery.class);
        when(query.check(eq(1L), any(), any())).thenReturn(preview());
        var controller =
                new CartAvailabilityController(
                        features,
                        service,
                        mock(MenuPickupDiscoveryService.class),
                        new com.gokulsweets.restaurant.order.service.MenuPreviewReads(
                                query, java.time.Clock.systemUTC()));
        var request =
                new CartAvailabilityController.Request(preview().today(), 1, List.of(), "PICKUP");
        when(service.check(1L, request.startDate(), 1, request.items(), false))
                .thenReturn(preview());
        when(service.check(1L, request.startDate(), 1, request.items(), true))
                .thenReturn(preview());
        assertThat(controller.check(1L, request, false, true).getBody()).isEqualTo(preview());
        assertThat(controller.check(1L, request, true, false).getBody()).isEqualTo(preview());
        assertThat(controller.check(1L, request, true, true).getBody())
                .isInstanceOf(CompactMenuAvailability.class);
    }

    @Test
    void httpContractKeepsLegacyBodiesAndSerializesOptInIndexes() throws Exception {
        var features = new EnhancementProperties();
        features.setSmartAvailability(true);
        var service = mock(CartAvailabilityService.class);
        var query = mock(com.gokulsweets.restaurant.order.service.MenuPreviewQuery.class);
        when(query.check(eq(1L), any(), any())).thenReturn(preview());
        when(service.check(eq(1L), any(), eq(1), any(), eq(true))).thenReturn(preview());
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new CartAvailabilityController(
                                        features,
                                        service,
                                        mock(MenuPickupDiscoveryService.class),
                                        new com.gokulsweets.restaurant.order.service
                                                .MenuPreviewReads(
                                                query, java.time.Clock.systemUTC())))
                        .build();
        String body =
                "{\"startDate\":\"2026-10-10\",\"days\":1,\"items\":[{\"productId\":1,\"quantity\":1}],\"fulfilmentType\":\"PICKUP\"}";
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/branches/1/availability?menuPreview=true&compact=true")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string("Cache-Control", "no-store"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.issueCatalog[0].productId")
                                .value(1))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.dates[0].slots[0].issueIndexes[1]")
                                .value(0))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.dates[0].slots[0].issues")
                                .doesNotExist());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/branches/1/availability?menuPreview=true")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.dates[0].slots[0].issues[1].code")
                                .value("NOT_READY"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.issueCatalog")
                                .doesNotExist());
    }
}
