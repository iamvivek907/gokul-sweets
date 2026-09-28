package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeliveryAcceptedQuoteControllerTest {
    @Test
    void previewIsHiddenUntilAllDeliveryDependenciesAreEnabled() {
        var flags = new EnhancementProperties();
        var service = mock(DeliveryAcceptedQuoteService.class);
        var controller = new DeliveryAcceptedQuoteController(flags, service);
        assertThat(controller.preview(null).getStatusCode().value()).isEqualTo(404);
        flags.setDeliveryAcceptedQuote(true);
        assertThat(controller.preview(null).getStatusCode().value()).isEqualTo(404);
        flags.setDeliveryAddressBoundaries(true);
        assertThat(controller.preview(null).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(service);
        flags.setDeliveryRiderHolds(true);
        var response = new DeliveryAcceptedQuoteService.Quote(1L, "2026-09-28", "11:00", "12:00",
                List.of(), "100.00", "0.00", "0.00", "0.00", "100.00", "INR", "2026-09-27T10:05:00Z", "token");
        when(service.preview(null)).thenReturn(response);
        assertThat(controller.preview(null).getBody()).isEqualTo(response);
    }
}
