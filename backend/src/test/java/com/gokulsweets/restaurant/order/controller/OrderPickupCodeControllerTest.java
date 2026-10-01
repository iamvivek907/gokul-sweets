package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.order.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderPickupCodeControllerTest {
    @Test
    void protectedPickupCodeResponseForbidsCachingIncludingWhenNoCodeIsAvailable() {
        var access = mock(VerifiedOrderAccess.class);
        var codes = mock(PickupCodeService.class);
        var controller = new OrderController(mock(OrderService.class), mock(OrderQueryService.class),
                mock(CheckoutQuoteService.class), mock(TrustedCheckoutIdentity.class), access, codes, mock(MobileCheckoutPreview.class));
        var request = new MockHttpServletRequest();
        when(codes.customerCode("ORDER-1")).thenReturn(new PickupCodeService.CustomerCode("0042"),
                new PickupCodeService.CustomerCode(null));
        var response = controller.pickupCode("ORDER-1", request);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().code()).isEqualTo("0042");
        var empty = controller.pickupCode("ORDER-1", request);
        assertThat(empty.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(empty.getBody().code()).isNull();
        verify(access, times(2)).requirePickupCode("ORDER-1", request);
    }
}
