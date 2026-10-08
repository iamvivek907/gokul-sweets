package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.staff.StaffUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerAccountControllerTest {
    private final OwnerAccountService accounts=mock(OwnerAccountService.class);
    private final StaffAuthorizationService authorization=mock(StaffAuthorizationService.class);
    private final OwnerAccountController controller=new OwnerAccountController(accounts,authorization);
    private MockHttpServletRequest request(){var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.1");return request;}
    @Test void publicResultsCannotBeCachedAndAttemptsUseServerObservedAddress() {
        when(accounts.status()).thenReturn(new OwnerAccountService.Status(false));
        assertThat(controller.status().getHeaders().getCacheControl()).isEqualTo("no-store");
        when(accounts.setup("setup","owner","password","Owner")).thenReturn(new OwnerAccountService.Created("owner","key"));
        var request=request();request.addHeader("X-Forwarded-For","attacker-supplied");
        var result=controller.setup(new OwnerAccountController.Setup("setup","owner","password","Owner"),request);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");verify(accounts).limit("setup","192.0.2.1");
        controller.recover(new OwnerAccountController.Recovery("key","owner","password"),request);
        verify(accounts).limit("recovery","192.0.2.1");verify(accounts).recover("key","owner","password");
    }
    @Test void authenticatedActionsUseCurrentStaffIdentityOnly() {
        var staff=new StaffUser();staff.setId(17L);when(authorization.getCurrentStaff()).thenReturn(staff);
        controller.account();verify(accounts).account(17L);
        var key=controller.key(new OwnerAccountController.Verification("password","mfa"),request());
        assertThat(key.getHeaders().getCacheControl()).isEqualTo("no-store");verify(accounts).limit("key:17","192.0.2.1");verify(accounts).recoveryKey(17L,"password","mfa");
        var renamed=controller.username(new OwnerAccountController.Rename("new","password","mfa"),request());
        assertThat(renamed.getStatusCode().value()).isEqualTo(204);assertThat(renamed.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(accounts).limit("rename:17","192.0.2.1");verify(accounts).rename(17L,"new","password","mfa");
    }
}
