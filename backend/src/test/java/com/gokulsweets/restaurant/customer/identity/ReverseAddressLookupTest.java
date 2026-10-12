package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReverseAddressLookupTest {
    @Test
    void indianAddressIsSuggestedForCustomerReviewAndMissingPostalStaysBlank() {
        var value =
                ReverseAddressLookup.parse(
                        """
{"status":"OK","results":[{"formatted_address":"Market Road, Tamkuhi, India","address_components":[{"long_name":"Tamkuhi","types":["locality"]},{"short_name":"IN","types":["country"]}]}]}
""");
        assertThat(value.addressLine()).isEqualTo("Market Road, Tamkuhi, India");
        assertThat(value.locality()).isEqualTo("Tamkuhi");
        assertThat(value.postalCode()).isEmpty();
        assertThat(value.attribution()).isEqualTo("Google Maps");
    }

    @Test
    void outsideIndiaEmptyAndDeniedResultsAreNotSavedAsAddresses() {
        assertThatThrownBy(
                        () ->
                                ReverseAddressLookup.parse(
                                        "{\"status\":\"REQUEST_DENIED\",\"results\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ReverseAddressLookup.parse(
                                        "{\"status\":\"OK\",\"results\":[{\"formatted_address\":\"Other"
                                            + " country\",\"address_components\":[{\"short_name\":\"US\",\"types\":[\"country\"]}]}]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
