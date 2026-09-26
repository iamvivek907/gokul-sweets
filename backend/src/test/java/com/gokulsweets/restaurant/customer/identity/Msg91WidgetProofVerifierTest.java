package com.gokulsweets.restaurant.customer.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Msg91WidgetProofVerifierTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnlyProviderVerifiedIndianMobile() throws Exception {
        var response = mapper.readTree("""
                {"type":"success","data":{"identifier":"919876543210"}}
                """);
        assertThat(Msg91WidgetProofVerifier.verifiedPhoneFromResponse(response))
                .isEqualTo("+919876543210");
    }

    @Test
    void rejectsFailureMissingIdentityAndWrongCountry() throws Exception {
        for (var json : new String[] {
                "{\"type\":\"error\",\"data\":{\"identifier\":\"919876543210\"}}",
                "{\"type\":\"success\",\"data\":{}}",
                "{\"type\":\"success\",\"data\":{\"identifier\":\"447700900000\"}}"
        }) {
            assertThatThrownBy(() -> Msg91WidgetProofVerifier.verifiedPhoneFromResponse(mapper.readTree(json)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
