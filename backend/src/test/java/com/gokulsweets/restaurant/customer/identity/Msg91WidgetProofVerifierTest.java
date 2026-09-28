package com.gokulsweets.restaurant.customer.identity;

import tools.jackson.databind.ObjectMapper;
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
    @Test
    void logsOnlyFixedProviderFailureCategories() throws Exception {
        var invalid = mapper.readTree("""
                {"type":"error","message":"Invalid access token: secret-123"}
                """);
        assertThat(Msg91WidgetProofVerifier.providerType(invalid)).isEqualTo("error");
        assertThat(Msg91WidgetProofVerifier.providerReason(invalid)).isEqualTo("invalid-token");
        var auth = mapper.readTree("""
                {"type":"error","message":"Authentication Failure: key secret-123"}
                """);
        assertThat(Msg91WidgetProofVerifier.providerReason(auth)).isEqualTo("authkey");
        assertThat(Msg91WidgetProofVerifier.providerReason(mapper.readTree("""
                {"type":"error","message":"personal data secret-123"}
                """))).isEqualTo("other");
    }

}
