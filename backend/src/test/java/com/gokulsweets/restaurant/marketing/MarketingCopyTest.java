package com.gokulsweets.restaurant.marketing;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class MarketingCopyTest {
    @Test
    void windowsUseIndiaTimeAndHaveExactBoundaries() {
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T06:29:59Z"))).isNull();
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T06:30:00Z"))).isEqualTo("LUNCH");
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T08:00:00Z"))).isNull();
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T10:30:00Z"))).isEqualTo("SNACKS");
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T13:30:00Z"))).isEqualTo("DINNER");
        assertThat(MarketingCopy.window(Instant.parse("2026-10-21T15:00:00Z"))).isNull();
    }

    @Test
    void rotationIsStableAndNeverReusesRecentCopy() {
        var customer = UUID.randomUUID();
        var day = LocalDate.of(2026, 10, 21);
        var first = MarketingCopy.choose(customer, day, "SNACKS", "EN", Set.of());
        assertThat(MarketingCopy.choose(customer, day, "SNACKS", "EN", Set.of())).isEqualTo(first);
        var used = new HashSet<String>();
        for (int i = 0; i < 4; i++) {
            var message = MarketingCopy.choose(customer, day, "SNACKS", "EN", used);
            assertThat(message).isNotNull();
            assertThat(used.add(message.key())).isTrue();
        }
        assertThat(MarketingCopy.choose(customer, day, "SNACKS", "EN", used)).isNull();
    }

    @Test
    void copyStaysShortAndContainsNoUnverifiedDiscountOrStockClaims() {
        for (String language : List.of("EN", "HINGLISH"))
            for (var copy : MarketingCopy.samples(language)) {
                assertThat(copy.title().length()).isLessThanOrEqualTo(60);
                assertThat(copy.body().length()).isLessThanOrEqualTo(140);
                assertThat(copy.title() + copy.body())
                        .doesNotContain("%", "₹", "in stock", "ready now");
            }
        assertThat(MarketingCopy.preference("Sweets")).isEqualTo("SWEETS");
        assertThat(MarketingCopy.preference("Snacks & Fast Food")).isEqualTo("SNACKS");
        assertThat(MarketingCopy.preference("Thalis & Combos")).isNull();
    }
}
