package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeliveryOrderControllerTest {
    @Test
    void requiresAllCheckoutFlagsAndPreservesIdempotencyAndTrustedIdentity() {
        var flags = new EnhancementProperties();
        var service = mock(DeliveryOrderCreationService.class);
        var identity = mock(TrustedCheckoutIdentity.class);
        var servletRequest = mock(HttpServletRequest.class);
        var controller = new DeliveryOrderController(flags, service, identity);
        assertThat(controller.create("key", null, servletRequest).getStatusCode().value()).isEqualTo(404);
        flags.setDeliveryCheckout(true);
        flags.setDeliveryAcceptedQuote(true);
        flags.setDeliveryRiderHolds(true);
        assertThat(controller.create("key", null, servletRequest).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(service, identity);

        flags.setDeliveryAddressBoundaries(true);
        assertThat(controller.create("key", null, servletRequest).getStatusCode().value()).isEqualTo(404);
        flags.setDeliveryCapacity(true);
        flags.setDeliveryZones(true);
        flags.setDeliveryLocalityCheck(true);
        flags.setCustomerConsentControls(true);
        flags.setCustomerOtpIdentity(true);
        var created = new DeliveryOrderCreationService.Created(1L, "GKS-ORDER", 2L, 3L,
                OrderStatus.PENDING_PAYMENT, BigDecimal.ONE,
                LocalDateTime.of(2026, 9, 27, 20, 0), LocalDateTime.of(2026, 9, 27, 19, 45));
        when(identity.token(servletRequest)).thenReturn("trusted-token");
        when(service.create(null, "key", "trusted-token")).thenReturn(created);
        var response = controller.create("key", null, servletRequest);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isEqualTo(created);
        verify(service).create(null, "key", "trusted-token");
    }
}
