package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.rebate.dto.ApplyRebateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RebateCustomerControllerTest {
    @Test
    void deniedVerifiedOrderNeverReadsOrChangesItsRebate() {
        var eligibility = mock(RebateEligibilityService.class);
        var application = mock(RebateApplicationService.class);
        var access = mock(VerifiedOrderAccess.class);
        var request = new MockHttpServletRequest();
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
                .when(access).requireOrder("GKS-FOREIGN", request);
        var controller = new RebateCustomerController(eligibility, application, access);

        assertThatThrownBy(() -> controller.getAvailableRebates("GKS-FOREIGN", request))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.applyRebate("GKS-FOREIGN", new ApplyRebateRequest("CODE"), request))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.removeRebate("GKS-FOREIGN", request))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(eligibility, application);
    }
}
